import { request } from '@playwright/test'
import * as fs from 'fs'
import * as path from 'path'
import { newApi } from './helpers/api'

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080'
const MAX_ATTEMPTS = 60
const INTERVAL_MS = 1_000
export const STORAGE_STATE = path.join(__dirname, '.auth', 'state.json')

/**
 * Wait for the app, then log in once and persist the session (cookies incl.
 * XSRF-TOKEN) for the browser-based specs via Playwright's storageState.
 * API-driven specs authenticate themselves through helpers/api.ts newApi().
 */
export default async function globalSetup() {
  const ctx = await request.newContext()
  let up = false
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      // /api/auth/me is permitAll — any HTTP response (200 or 401) means the app is up.
      const res = await ctx.get(`${BASE_URL}/api/auth/me`, { timeout: 3_000 })
      if (res.status() < 500) {
        up = true
        break
      }
    } catch {
      // service not up yet — retry
    }
    await new Promise(r => setTimeout(r, INTERVAL_MS))
  }
  await ctx.dispose()
  if (!up) throw new Error(`Timed out waiting for monohull-app at ${BASE_URL}`)

  const authed = await newApi()
  fs.mkdirSync(path.dirname(STORAGE_STATE), { recursive: true })
  await authed.storageState({ path: STORAGE_STATE })
  await authed.dispose()
}
