import { defineConfig, devices } from '@playwright/test';
import { CoverageReportOptions } from 'monocart-coverage-reports';

const cro: CoverageReportOptions = {
  name: 'BDeploy Coverage Reports',
  outputDir: 'test-reports/monocart',
  entryFilter: (entry) => {
    const url = entry.url as string;
    return (
      !url.includes('@vite') &&
      !url.includes('@fs') &&
      !url.includes('fonts.googleapis.com') &&
      !url.includes('node_modules')
    );
  },
  sourceFilter: (source) => source.search(/src\/.+/) !== -1,
  reports: [
    ['console-summary', { metrics: ['branches', 'lines'] }],
    ['v8', { outputFile: 'v8.html', inline: true, metrics: ['branches', 'lines'] }],
    ['lcovonly', { file: 'lcov/coverage.lcov.info' }],
  ],
};

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './playwright',
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env['CI'],
  /* Retry on CI only */
  retries: process.env['CI'] ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env['CI'] ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: [
    ['list'],
    ['html', { outputFolder: 'test-reports/playwright', open: 'never' }],
    ['junit', { outputFile: 'test-reports/playwright/results.xml' }],
    [
      'monocart-reporter',
      {
        name: 'BDeploy Test Report',
        outputFile: 'test-reports/monocart/index.html',
        coverage: cro,
      },
    ],
  ],
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: 'http://localhost:4210',

    /* Keep failure screenshots */
    screenshot: 'only-on-failure',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'retain-on-failure',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    //{
    //  name: 'firefox',
    //  use: { ...devices['Desktop Firefox'] },
    //},

    //{
    //  name: 'webkit',
    //  use: { ...devices['Desktop Safari'] },
    //},

    /* Test against mobile viewports. */
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: { ...devices['iPhone 12'] },
    // },

    /* Test against branded browsers. */
    // {
    //   name: 'Microsoft Edge',
    //   use: { ...devices['Desktop Edge'], channel: 'msedge' },
    // },
    // {
    //   name: 'Google Chrome',
    //   use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    // },
  ],

  /* Run your local dev server before starting the tests */
  // webServer: {
  //   command: 'npm run start',
  //   url: 'http://127.0.0.1:3000',
  //   reuseExistingServer: !process.env.CI,
  // },
});
