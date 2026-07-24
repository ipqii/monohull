import { test, expect, APIRequestContext } from '@playwright/test'
import { newAuthedApi, uniqueId, createImageConfig } from '../helpers/api'

/**
 * Failure-path coverage (MXF-21): a build that fails must end in ERROR with a
 * plain-English, actionable error in the build log (not a raw docker HTTP body),
 * teardown of the partially built environment must complete, and container
 * actions against never-created containers must fail with a clear 400.
 *
 * Uses images that cannot be pulled, so no Maximo images are needed and the
 * failure happens within seconds — but it exercises the real daemon, the real
 * build pipeline, and the real teardown sweep.
 */

const BOGUS_IMAGE = 'docker.io/library/monohull-e2e-no-such-image:1'

async function createFailingEnvironment(api: APIRequestContext) {
  const suffix = uniqueId()
  const config = await createImageConfig(api, {
    client: suffix,
    project: 'failpath',
    appImage: BOGUS_IMAGE,
    dbImage: BOGUS_IMAGE,
    admImage: BOGUS_IMAGE,
  })
  const createRes = await api.post('/api/environments', {
    data: {
      name: `${suffix}-failpath-1`,
      imageConfigId: config.id,
      staticPorts: false,
      includeMock: false,
      includeSmtp: false,
    },
  })
  expect(createRes.ok(), await createRes.text()).toBeTruthy()
  const env = await createRes.json()

  await expect
    .poll(async () => {
      const res = await api.get(`/api/environments/${env.id}`)
      return res.ok() ? (await res.json()).status : 'unreachable'
    }, { timeout: 120_000, intervals: [2_000] })
    .toBe('ERROR')

  return { config, env }
}

test.describe('build failure paths', () => {
  test.setTimeout(180_000)

  test('unpullable image: build ends ERROR with an actionable error, remove cleans up', async () => {
    const api = await newAuthedApi()
    const { config, env } = await createFailingEnvironment(api)

    // The log tail must carry the translated cause+fix, not a raw HTTP body.
    const historyRes = await api.get(`/api/environments/${env.id}/logs/history`)
    expect(historyRes.ok()).toBeTruthy()
    const history = await historyRes.json()
    const text = history.lines.map((l: { line: string }) => l.line).join('\n')
    expect(text).toMatch(/registry refused the image pull|Image not found in the registry/)

    // Teardown of the partially built env completes (204) and it leaves the list.
    const del = await api.delete(`/api/environments/${env.id}`)
    expect(del.status()).toBe(204)
    const list = await (await api.get('/api/environments')).json()
    expect(list.find((e: { id: number }) => e.id === env.id)).toBeUndefined()

    // The removed env keeps referencing its image config for history, so deleting the
    // config is refused — but with a clear 409, not a raw 500.
    const delConfig = await api.delete(`/api/config/images/${config.id}`)
    expect(delConfig.status()).toBe(409)
    expect((await delConfig.json()).error).toContain('still references')
  })

  test('stop on a never-created container is a clear 400, not a 500', async () => {
    const api = await newAuthedApi()
    const { config, env } = await createFailingEnvironment(api)

    // The pull failed before any container was created, so every container row
    // has no docker container behind it.
    const detail = await (await api.get(`/api/environments/${env.id}`)).json()
    const container = detail.containers[0]
    const stop = await api.post(`/api/containers/${container.id}/stop`)
    expect(stop.status()).toBe(400)
    const body = await stop.json()
    expect(body.error).toContain('was never created')

    await api.delete(`/api/environments/${env.id}`)
  })
})
