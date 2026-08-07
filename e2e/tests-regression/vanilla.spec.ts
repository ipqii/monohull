import { test, expect, request as pwRequest, APIRequestContext } from '@playwright/test'

/**
 * Post-merge regression against a REAL vanilla Maximo environment.
 *
 * The ci/ harness has already stood up a throwaway Monohull instance and
 * launched the vanilla profile (ci/seed.sh); this spec waits for the build,
 * verifies Maximo actually serves, pins known log regressions, and tears the
 * environment down through the product's own API.
 *
 * Everything is driven over the bearer API key (CSRF-exempt), not the browser
 * session. Skips itself unless CI_ENV_ID + the key are present, so the default
 * e2e suite is unaffected.
 */

const ENV_ID = process.env.CI_ENV_ID
const API_KEY = process.env.MONOHULL_API_KEY ?? process.env.CI_API_KEY
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8899'
const WAIT_MINUTES = Number(process.env.CI_WAIT_MINUTES ?? 75)
const MAXADMIN_PASSWORD = process.env.CI_MAXADMIN_PASSWORD ?? 'maxadmin'

test.describe('vanilla Maximo regression', () => {
  test.skip(!ENV_ID || !API_KEY,
    'needs CI_ENV_ID and MONOHULL_API_KEY (seeded by ci/seed.sh) - not part of the default suite')

  let api: APIRequestContext

  test.beforeAll(async () => {
    api = await pwRequest.newContext({
      baseURL: BASE_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${API_KEY}` },
    })
  })

  test.afterAll(async () => {
    await api?.dispose()
  })

  /** Last chunk of the build log, attached to the report on failure. */
  async function attachBuildLog(testInfo: { attach: Function }) {
    try {
      const r = await api.get(`/api/environments/${ENV_ID}/logs/history?limit=500`)
      if (r.ok()) {
        const page = await r.json()
        const lines = (page.lines ?? []).map((l: { line: string }) => l.line).join('\n')
        await testInfo.attach('build-log-tail', { body: lines, contentType: 'text/plain' })
      }
    } catch { /* attaching diagnostics must never mask the real failure */ }
  }

  test('builds to RUNNING, serves Maximo, JMS is clean, tears down', async ({}, testInfo) => {
    test.setTimeout((WAIT_MINUTES + 15) * 60_000)

    // --- 1. Wait for the environment build -------------------------------
    const deadline = Date.now() + WAIT_MINUTES * 60_000
    let env: any
    for (;;) {
      const r = await api.get(`/api/environments/${ENV_ID}`)
      expect(r.ok(), `GET environment ${ENV_ID} -> ${r.status()}`).toBeTruthy()
      env = await r.json()
      if (env.status === 'RUNNING') break
      if (env.status === 'ERROR') {
        await attachBuildLog(testInfo)
        throw new Error(`environment ${env.name} finished in ERROR - see build-log-tail attachment`)
      }
      if (Date.now() > deadline) {
        await attachBuildLog(testInfo)
        throw new Error(`environment ${env.name} still ${env.status} after ${WAIT_MINUTES} min`)
      }
      await new Promise(resolve => setTimeout(resolve, 10_000))
    }

    // --- 2. Maximo actually serves ---------------------------------------
    const config = await (await api.get(`/api/environments/${ENV_ID}/config`)).json()
    expect(config.appHttpPort, 'environment has a published HTTP port').toBeTruthy()
    const maximoHost = new URL(BASE_URL).hostname
    const maximo = `http://${maximoHost}:${config.appHttpPort}`

    // Maximo can still be settling right after RUNNING: the pipeline's last step
    // swaps server.xml (Liberty hot-reloads it), and MXServer finishes its own
    // init after the UI war first serves (a 500/BMXAA7901E window of a couple of
    // minutes). Judge "serves within a settle window", not a single request.
    const anon = await pwRequest.newContext()
    try {
      const settleDeadline = Date.now() + 10 * 60_000
      let uiStatus = 0
      for (;;) {
        try {
          const ui = await anon.get(`${maximo}/maximo/`, { maxRedirects: 5, timeout: 60_000 })
          uiStatus = ui.status()
        } catch { uiStatus = 0 /* connection refused while Liberty restarts */ }
        if (uiStatus === 200 || Date.now() > settleDeadline) break
        await new Promise(resolve => setTimeout(resolve, 15_000))
      }
      expect(uiStatus, `${maximo}/maximo/ should serve the UI within the settle window`).toBe(200)

      const maxauth = Buffer.from(`maxadmin:${MAXADMIN_PASSWORD}`).toString('base64')
      let whoamiStatus = 0
      for (;;) {
        try {
          const whoami = await anon.get(`${maximo}/maximo/oslc/whoami`, {
            headers: { maxauth },
            timeout: 60_000,
          })
          whoamiStatus = whoami.status()
        } catch { whoamiStatus = 0 }
        if (whoamiStatus === 200 || Date.now() > settleDeadline) break
        await new Promise(resolve => setTimeout(resolve, 15_000))
      }
      expect(whoamiStatus, 'oslc/whoami with maxauth should authenticate within the settle window').toBe(200)
    } finally {
      await anon.dispose()
    }

    // --- 3. Log regressions ----------------------------------------------
    const app = (env.containers ?? []).find((c: any) => c.role === 'APP')
    expect(app, 'environment has an APP container').toBeTruthy()
    const logResponse = await api.get(`/api/containers/${app.id}/logs?tail=2000`)
    expect(logResponse.ok()).toBeTruthy()
    const log = ((await logResponse.json()) as string[]).join('\n')

    expect(log, 'Maximo reached readiness (BMXAA6472I)').toContain('BMXAA6472I')
    // Pins issue #7 / PR #8: the embedded JMS config must load on every
    // server.xml flavor, so the messaging engine starts...
    expect(log, 'JMS server started (CWSID0108I)').toContain('CWSID0108I')
    // ...and the JMSQSEQCONSUMER cron never logs the missing-JNDI error.
    expect(log, 'no jms/maximo JNDI errors').not.toContain(
      'Intermediate context does not exist: jms/maximo')

    // --- 4. Teardown through the product ---------------------------------
    const del = await api.delete(`/api/environments/${ENV_ID}`)
    expect(del.status(), 'DELETE tears the environment down').toBe(204)
    const remaining = await (await api.get('/api/environments')).json()
    expect(remaining.find((e: any) => e.id === Number(ENV_ID)),
      'environment gone from the list').toBeUndefined()
  })
})
