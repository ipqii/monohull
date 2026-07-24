import { test, expect, APIRequestContext } from '@playwright/test'
import {
  createImageConfig, deleteImageConfig, listImageConfigs, newApi, uniqueId,
} from '../helpers/api'

test.describe('Image config CRUD', () => {
  let api: APIRequestContext
  const createdIds: number[] = []

  test.beforeAll(async () => {
    api = await newApi()
  })

  test.afterEach(async () => {
    for (const id of createdIds.splice(0)) {
      await deleteImageConfig(api, id)
    }
  })

  test.afterAll(async () => {
    await api.dispose()
  })

  test('seeded image config appears in the Environments list', async ({ page }) => {
    const tag = uniqueId()
    const created = await createImageConfig(api, {
      client: `client-${tag}`,
      project: `project-${tag}`,
      maximoVersion: 'MAS',
      dbName: 'maxdb76',
    })
    createdIds.push(created.id)

    await page.goto('/config/environments')
    await expect(page.getByRole('heading', { name: 'Environments' })).toBeVisible()
    await expect(page.getByText(`client-${tag}`).first()).toBeVisible()
  })

  test('extras round-trip via the API', async ({ }) => {
    const tag = uniqueId()
    const created = await createImageConfig(api, {
      client: `client-${tag}`,
      project: `project-${tag}`,
      admExtraEnv: [{ key: 'AWS_PROFILE', value: 'test-profile' }],
      admExtraBinds: [{ hostPath: '/tmp/aws', containerPath: '/root/.aws', readOnly: true }],
    } as any)
    createdIds.push(created.id)

    const all = await listImageConfigs(api)
    const reloaded = all.find((c: any) => c.id === created.id)
    expect(reloaded).toBeTruthy()
    expect(reloaded.admExtraEnv).toEqual([{ key: 'AWS_PROFILE', value: 'test-profile' }])
    expect(reloaded.admExtraBinds).toEqual([
      { hostPath: '/tmp/aws', containerPath: '/root/.aws', readOnly: true },
    ])
  })

  test('UI full-page editor creates a new image config', async ({ page }) => {
    const tag = uniqueId()
    await page.goto('/config/environments')

    // MADE-14 replaced the New Environment dialog with a full-page editor.
    await page.getByRole('button', { name: 'New Environment' }).click()
    await page.waitForURL('**/config/environments/new')

    await page.getByLabel('Client').fill(`client-${tag}`)
    await page.getByLabel('Project').fill(`project-${tag}`)

    // Maximo Version is the first MUI Select (combobox) on the page. Its
    // accessible-name wiring varies between MUI versions, so we locate it
    // positionally — Database Vendor (DB2) and Pipeline come after it.
    await page.locator('[role="combobox"]').first().click()
    await page.getByRole('option', { name: 'MAS' }).click()

    await page.getByLabel('App Image').fill('registry.example.com/app:test')
    await page.getByLabel('DB Image').fill('registry.example.com/db2:test')
    await page.getByLabel('ADM Image').fill('registry.example.com/adm:test')

    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().endsWith('/api/config/images') && r.request().method() === 'POST'),
      page.getByRole('button', { name: 'Create Environment' }).click(),
    ])
    const created = await response.json()
    createdIds.push(created.id)

    // Saving navigates back to the list, where the new card shows.
    await page.waitForURL('**/config/environments')
    await expect(page.getByText(`client-${tag}`).first()).toBeVisible()
  })
})
