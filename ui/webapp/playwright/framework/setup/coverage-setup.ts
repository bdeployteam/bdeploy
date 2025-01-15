import test, { Browser, Page } from '@playwright/test';
import { addCoverageReport } from 'monocart-reporter';

export const startCoverage = async (browser: Browser, page: Page) => {
  const isChromium = browser.browserType().name() === 'chromium';

  // coverage API is chromium only
  if (isChromium) {
    await Promise.all([
      page.coverage.startJSCoverage({
        resetOnNavigation: false,
      }),
      page.coverage.startCSSCoverage({
        resetOnNavigation: false,
      }),
    ]);
  }
};

export const stopCoverage = async (browser: Browser, page: Page) => {
  const isChromium = browser.browserType().name() === 'chromium';

  // collect coverage after the test
  if (isChromium) {
    const [jsCoverage, cssCoverage] = await Promise.all([
      page.coverage.stopJSCoverage(),
      page.coverage.stopCSSCoverage(),
    ]);
    const coverageList = [...jsCoverage, ...cssCoverage];

    if (coverageList?.length) {
      // add coverage to monocart reporter
      await addCoverageReport(coverageList, test.info());
    }
  }
};
