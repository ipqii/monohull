import { test, expect, APIRequestContext } from '@playwright/test'
import {
  createCustomAction, deleteCustomAction, listCustomActions, newApi, uniqueId,
} from '../helpers/api'

test.describe('Custom actions', () => {
  let api: APIRequestContext
  const createdIds: number[] = []

  test.beforeAll(async () => {
    api = await newApi()
  })

  test.afterEach(async () => {
    for (const id of createdIds.splice(0)) {
      await deleteCustomAction(api, id)
    }
  })

  test.afterAll(async () => {
    await api.dispose()
  })

  test('built-in actions are present', async () => {
    const all = await listCustomActions(api)
    const keys = all.map((a: any) => a.actionKey)
    expect(keys).toEqual(expect.arrayContaining([
      'run-updatedb-preprocessor', 'build-ear', 'run-updatedb', 'restart-was',
    ]))
  })

  test('runAsUser persists across save+reload', async () => {
    const tag = uniqueId()
    const created = await createCustomAction(api, {
      name: `act-${tag}`,
      targetRole: 'ADM',
      command: '/bin/true',
      runAsUser: 'maximo',
    })
    createdIds.push(created.id)

    expect(created.runAsUser).toBe('maximo')

    const all = await listCustomActions(api)
    const reloaded = all.find((a: any) => a.id === created.id)
    expect(reloaded.runAsUser).toBe('maximo')
  })

  test('UI shows the action created via API', async ({ page }) => {
    const tag = uniqueId()
    const name = `ui-act-${tag}`
    const created = await createCustomAction(api, {
      name,
      targetRole: 'APP',
      command: '/bin/true',
    })
    createdIds.push(created.id)

    await page.goto('/config/actions')
    await expect(page.getByRole('heading', { name: 'Actions', exact: true })).toBeVisible()
    // The action name appears in two places (card title + footer "Key: ..."); target the heading.
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible({ timeout: 15_000 })
  })
})
