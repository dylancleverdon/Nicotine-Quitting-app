import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests',
  timeout: 60_000,
  use: {
    baseURL: 'http://localhost:4173/Nicotine-Quitting-app/',
    launchOptions: process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {},
    viewport: { width: 390, height: 844 },
  },
  webServer: { command: 'npx vite preview --port 4173 --strictPort', url: 'http://localhost:4173/Nicotine-Quitting-app/', reuseExistingServer: true },
})
