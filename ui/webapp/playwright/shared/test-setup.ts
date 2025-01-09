import { test as testBase, expect } from '@playwright/test';
import { addCoverageReport } from 'monocart-reporter';

interface AppFixtures {
  coverageAutoFixture;
}

const test = testBase.extend<AppFixtures>({
  coverageAutoFixture: [
    async ({ browser, page }, use) => {
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

      // run all tests :)
      await use('coverageAutoFixture');

      // collect coverage after the test
      if (isChromium) {
        const [jsCoverage, cssCoverage] = await Promise.all([
          page.coverage.stopJSCoverage(),
          page.coverage.stopCSSCoverage(),
        ]);
        const coverageList = [...jsCoverage, ...cssCoverage];

        // add coverage to monocart reporter
        await addCoverageReport(coverageList, test.info());
      }
    },
    {
      auto: true,
    },
  ],
});
export { test, expect };
