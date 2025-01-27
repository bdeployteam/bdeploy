import { expect, Locator, Page } from '@playwright/test';

export class MainMenu {
  private readonly _menu: Locator;
  private readonly _hamburger: Locator;
  private readonly _serverType: Locator;

  constructor(private readonly page: Page) {
    this._menu = page.locator('app-main-nav-menu');
    this._hamburger = this._menu.locator('.hamburger');
    this._serverType = this._menu.locator('.local-server-type');
  }

  private async expectMenuCollapsed() {
    expect((await this._menu.boundingBox()).width).toBe(64);
  }

  private async expectMenuExpanded() {
    expect((await this._menu.boundingBox()).width).toBe(220);
  }

  async expandMainMenu() {
    await this.expectMenuCollapsed();
    await this._hamburger.click();
    await this.page.waitForTimeout(100); // should not normally do this - but hardcoded animations :|
    await this.expectMenuExpanded();
  }

  async collapseMainMenu() {
    await this.expectMenuExpanded();
    await this._hamburger.click();
    await this.expectMenuCollapsed();
  }

  async expectServerType(type: string) {
    await this.expectMenuExpanded();
    await expect(this._serverType).toContainText(type);
  }

  getNavButton(id: string) {
    return this._menu.getByTestId(id);
  }

  async scrollIntoView() {
    try {
      // need to check whether the menu *exists* at all.
      await expect(this._menu).toBeAttached();
    } catch (error) {
      // ignore if the menu does not exist.
      return;
    }

    await this._menu.scrollIntoViewIfNeeded();
  }
}
