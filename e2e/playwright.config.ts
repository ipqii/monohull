import { defineConfig, devices } from '@playwright/test'
import * as path from 'path'

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080'
// Written by global-setup after an API login; gives browser specs an authenticated session.
const STORAGE_STATE = path.join(__dirname, '.auth', 'state.json')

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  globalSetup: './global-setup.ts',
  use: {
    baseURL: BASE_URL,
    storageState: STORAGE_STATE,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
