import { defineConfig, devices } from '@playwright/test';
import { CoverageReportOptions } from 'monocart-coverage-reports';

const cro: CoverageReportOptions = {
  name: 'BDeploy Coverage Reports',
  outputDir: 'playwright/results/reports/monocart',
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
    ['console-summary', { metrics: ['functions', 'branches', 'lines'] }],
    ['v8', { outputFile: 'coverage.html', inline: true, metrics: ['functions', 'branches', 'lines'] }],
    ['lcovonly', { file: 'lcov/coverage.lcov.info' }],
  ],
};

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  outputDir: './playwright/results/results',
  /* Run tests in files in parallel */
  fullyParallel: !process.env['CI'],
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env['CI'],
  /* Retry on CI only, but only once */
  retries: process.env['CI'] ? 3 : 0,
  maxFailures: process.env['CI'] ? 5 : 5,
  /* Restrict parallel tests on CI. */
  workers: process.env['CI'] ? 2 : 4,
  /* default test timeout */
  timeout: process.env['CI'] ? 90000 : 30000,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright/results/reports/playwright', open: 'never' }],
    ['junit', { outputFile: 'playwright/results/reports/playwright/results.xml' }],
    [
      'monocart-reporter',
      {
        name: 'BDeploy Test Report',
        outputFile: 'playwright/results/reports/monocart/index.html',
        coverage: cro,
      },
    ],
  ],

  /* report up to 10 slow tests, where slow is > 30 seconds */
  reportSlowTests: {
    max: 10,
    threshold: 30000
  },

  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Keep failure screenshots */
    screenshot: 'only-on-failure',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },

  /* Timeout when expecting something to happen */
  expect: {
    timeout: process.env['CI'] ? 40000 : 5000
  },

  projects: [
    {
      name: 'chrome-all',
      use: { ...devices['Desktop Chrome'] },
      testDir: './playwright/tests'
    },
    {
      name: 'firefox-features',
      use: { ...devices['Desktop Firefox'] },
      testDir: './playwright/tests/features'
    }
  ],
});
