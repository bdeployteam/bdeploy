import { expect, Locator, Page } from '@playwright/test';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';

export abstract class BaseArea {
  constructor(protected readonly page: Page) {
  }

  /**
   * Creates a screenshot of the current page, or (if requested) the area this is called on only.
   *
   * @param name name of the file to put in the screenshots folder.
   * @param dialogOrPanelOnly if set, limit the screenshot to the area this method is called on, otherwise whole page.
   * @param waitForLoading wait until all spinners on dialogs disappear. There are situations where popups will block
   *                       the spinner from disappearing intentionally. In this case set this to false to proceed
   *                       despite the spinner still being present.
   */
  async screenshot(name: string, dialogOrPanelOnly = false, waitForLoading = true) {
    // why is this needed? sometimes it scrolls to an invisible area where the panel is parked offscreen...
    await new MainMenu(this.page).scrollIntoView();

    if (waitForLoading) {
      // wait for dialog loading spinners to disappear
      await expect(this.page.locator('app-bd-loading-overlay').locator('mat-spinner')).not.toBeVisible();
    }

    if (dialogOrPanelOnly) {
      await this.getArea().screenshot({ path: `playwright/results/screenshots/${name}.png` });
    } else {
      await this.page.screenshot({ path: `playwright/results/screenshots/${name}.png` });
    }
  }

  getTableRowContaining(name: string) {
    return this.getArea().locator('tbody').getByRole('row', { name: name }).first();
  }

  getOverlayContainer() {
    return this.page.locator('.cdk-overlay-container');
  }

  async closeOverlayByBackdropClick() {
    await this.getOverlayContainer().locator('.cdk-overlay-backdrop').click({ position: { x: 0, y: 0 } });
  }

  async scrollIntoView() {
    await this.getArea().scrollIntoViewIfNeeded();
  }

  protected abstract getArea(): Locator;
}