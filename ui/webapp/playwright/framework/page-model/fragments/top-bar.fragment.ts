import { Locator, Page } from '@playwright/test';
import { UserSettingsPanel } from '@bdeploy-pom/panels/user-settings.panel';

export class TopBar {
  private readonly _top: Locator;

  constructor(private readonly page: Page) {
    this._top = page.locator('app-main-nav-top');
  }

  async getUserSettings() {
    await this._top.getByLabel('User Settings').click();
    const userSettings = new UserSettingsPanel(this.page);
    await userSettings.expectOpen();
    return Promise.resolve(userSettings);
  }

  getSearchField() {
    return this._top.locator('app-bd-search-field').locator('input');
  }

  async screenshot(name: string) {
    // use fixed bounding box to avoid overlaying dialog as much as possible.
    await this.page.screenshot({
      path: `playwright/results/screenshots/${name}.png`,
      clip: { x: 0, y: 0, width: 1280, height: 80 }
    });
  }
}
