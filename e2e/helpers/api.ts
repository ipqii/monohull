import { APIRequestContext, request } from '@playwright/test'

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080'

export type ImageConfigPayload = Partial<{
  client: string
  project: string
  maximoVersion: string
  appImage: string
  dbImage: string
  admImage: string
  dbVendor: string
  dbName: string
  hostVolumePath: string
  dbVolumeName: string
  workspacePath: string
  pipelineId: number | null
}>

export type CustomActionPayload = Partial<{
  name: string
  description: string
  targetRole: string
  command: string
  workingDir: string
  timeoutSeconds: number
  imageConfigId: number
  autoRun: boolean
  executionType: string
  allowedExitCodes: string
  runAsUser: string
}>

/**
 * Returns a short random suffix safe to embed in resource names so parallel/repeat
 * runs don't collide. Format: 8 hex chars, prefixed with `e2e-`.
 */
export function uniqueId(): string {
  return 'e2e-' + Math.random().toString(16).slice(2, 10)
}

const E2E_USERNAME = process.env.E2E_USERNAME ?? 'admin'
const E2E_PASSWORD = process.env.E2E_PASSWORD ?? 'admin'

type StorageState = Awaited<ReturnType<APIRequestContext['storageState']>>

function cookieValue(state: StorageState, name: string): string | undefined {
  return state.cookies.find(c => c.name === name)?.value
}

/**
 * Authenticated API context. /api/** requires a session since the login screen
 * landed (MADE-2), and mutating calls must echo the XSRF-TOKEN cookie in the
 * X-XSRF-TOKEN header (axios does this in the SPA; here it's baked into the
 * context's default headers). Credentials come from E2E_USERNAME/E2E_PASSWORD,
 * matching the admin the compose app seeds on first boot.
 */
export async function newApi(): Promise<APIRequestContext> {
  // storageState MUST be explicitly empty: inside a test run, request.newContext()
  // inherits the config's storageState — i.e. the browser session global-setup saved.
  // Logging in while carrying that session makes Spring's session-fixation protection
  // rotate ITS id, silently logging the browser out for every later UI test.
  const bootstrap = await request.newContext({
    baseURL: BASE_URL,
    storageState: { cookies: [], origins: [] },
  })
  await bootstrap.get('/api/auth/me') // materialises the XSRF-TOKEN cookie
  let state = await bootstrap.storageState()
  const preLoginToken = cookieValue(state, 'XSRF-TOKEN')
  if (!preLoginToken) throw new Error('No XSRF-TOKEN cookie from /api/auth/me')

  const login = await bootstrap.post('/api/auth/login', {
    headers: { 'X-XSRF-TOKEN': preLoginToken },
    form: { username: E2E_USERNAME, password: E2E_PASSWORD },
  })
  if (login.status() !== 200) {
    throw new Error(`e2e login failed (${login.status()}) for user "${E2E_USERNAME}" — `
      + 'is MONOHULL_ADMIN_PASSWORD set on the app so the admin account gets seeded?')
  }

  // Spring Security clears the CSRF token on login (CsrfAuthenticationStrategy); the
  // fresh one only materialises on the next response. Without this GET the saved
  // cookie is the stale pre-login token and every later mutation 403s.
  await bootstrap.get('/api/auth/me')

  state = await bootstrap.storageState()
  const token = cookieValue(state, 'XSRF-TOKEN') ?? preLoginToken
  const ctx = await request.newContext({
    baseURL: BASE_URL,
    storageState: state,
    extraHTTPHeaders: { 'X-XSRF-TOKEN': token },
  })
  await bootstrap.dispose()
  return ctx
}

/** Alias making authentication explicit at call sites. */
export const newAuthedApi = newApi

export async function createImageConfig(api: APIRequestContext, payload: ImageConfigPayload) {
  const defaults: ImageConfigPayload = {
    client: 'e2e-client',
    project: 'e2e-project',
    maximoVersion: 'MAS',
    appImage: 'registry.example.com/app:test',
    dbImage: 'registry.example.com/db2:test',
    admImage: 'registry.example.com/adm:test',
    dbVendor: 'DB2',
    dbName: 'maxdb76',
    pipelineId: null,
  }
  const res = await api.post('/api/config/images', { data: { ...defaults, ...payload } })
  if (!res.ok()) throw new Error(`createImageConfig failed: ${res.status()} ${await res.text()}`)
  return await res.json()
}

export async function deleteImageConfig(api: APIRequestContext, id: number) {
  const res = await api.delete(`/api/config/images/${id}`)
  if (!res.ok() && res.status() !== 404) {
    throw new Error(`deleteImageConfig failed: ${res.status()} ${await res.text()}`)
  }
}

export async function listImageConfigs(api: APIRequestContext) {
  const res = await api.get('/api/config/images')
  if (!res.ok()) throw new Error(`listImageConfigs failed: ${res.status()}`)
  return await res.json()
}

export async function createCustomAction(api: APIRequestContext, payload: CustomActionPayload) {
  const defaults: CustomActionPayload = {
    name: 'e2e-action',
    targetRole: 'ADM',
    command: '/bin/true',
    executionType: 'EXEC',
    timeoutSeconds: 60,
  }
  const res = await api.post('/api/config/actions', { data: { ...defaults, ...payload } })
  if (!res.ok()) throw new Error(`createCustomAction failed: ${res.status()} ${await res.text()}`)
  return await res.json()
}

export async function deleteCustomAction(api: APIRequestContext, id: number) {
  const res = await api.delete(`/api/config/actions/${id}`)
  if (!res.ok() && res.status() !== 404) {
    throw new Error(`deleteCustomAction failed: ${res.status()} ${await res.text()}`)
  }
}

export async function listCustomActions(api: APIRequestContext) {
  const res = await api.get('/api/config/actions')
  if (!res.ok()) throw new Error(`listCustomActions failed: ${res.status()}`)
  return await res.json()
}
