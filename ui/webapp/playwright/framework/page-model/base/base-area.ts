import { Locator, Page } from '@playwright/test';

export abstract class BaseArea {
  constructor(protected readonly page: Page) {
  }

  async screenshot(name: string, dialogOrPanelOnly = false) {
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

  protected abstract getArea(): Locator;
}