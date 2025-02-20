import { Browser, expect, Page, test as testBase } from '@playwright/test';
import { startCoverage, stopCoverage } from './coverage-setup';
import { BackendApi } from '@bdeploy-backend';

interface BDeployFixtures {
  standalone: Page;
  central: Page;
  managed: Page;
}

const useAuthAndCoverage = async (url: string, browser: Browser, use: (p: Page) => Promise<void>, disableActions = false) => {
  const context = await browser.newContext({ baseURL: url });

  if (disableActions) {
    await BackendApi.mockRemoveActions(context);
  }

  const page = await context.newPage();

  // authenticate
  await context.request.post('/api/auth/session', { data: { user: 'admin', password: 'adminadminadmin' } });

  // start recording coverage information.
  await startCoverage(browser, page);

  // automatically navigate to the start page so not every test needs to.
  await page.goto('/');

  // run all further fixtures and tests.
  await use(page);

  // stop coverage and clean up
  await stopCoverage(browser, page);
  await page.close();
  await context.close();
};

const test = testBase.extend<BDeployFixtures>({
  standalone: async ({ browser }, use) => {
    await useAuthAndCoverage('http://localhost:4210', browser, use, true);
  },
  central: async ({ browser }, use) => {
    await useAuthAndCoverage('http://localhost:4211', browser, use);
  },
  managed: async ({ browser }, use) => {
    await useAuthAndCoverage('http://localhost:4212', browser, use);
  },
});
export { expect, test };
