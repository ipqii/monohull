import { test, expect } from '@playwright/test'

test.describe('Dashboard', () => {
  test('loads and shows the sidebar nav', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()

    // Sidebar items
    for (const label of ['Dashboard', 'Environments', 'Actions', 'Pipelines', 'Registry']) {
      await expect(page.getByRole('link', { name: label })).toBeVisible()
    }
  })

  test('navigates between pages via the sidebar', async ({ page }) => {
    await page.goto('/')

    await page.getByRole('link', { name: 'Environments' }).click()
    await page.waitForURL('**/config/environments')

    await page.getByRole('link', { name: 'Actions' }).click()
    await page.waitForURL('**/config/actions')

    await page.getByRole('link', { name: 'Pipelines' }).click()
    await page.waitForURL('**/pipelines')
  })
})
