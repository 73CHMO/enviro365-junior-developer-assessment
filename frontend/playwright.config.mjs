import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/browser',
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4200',
    browserName: 'chromium',
    channel: 'chrome',
    viewport: { width: 1440, height: 1100 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'node server.mjs',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 15000,
  },
});
