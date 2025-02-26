import { expect, Locator, Page } from '@playwright/test';
import { BaseArea } from '@bdeploy-pom/base/base-area';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';

export class BaseDialog extends BaseArea {
  private readonly _dialog: Locator;

  constructor(page: Page, selector: string, section = 'app-main-nav-content') {
    super(page);
    this._dialog = page.locator(section).locator(selector).locator('app-bd-dialog');
  }

  async expectOpen() {
    // attention: the dialog root element has no size and is thus not visible.
    await expect(this._dialog).toBeAttached();
    await expect(this.getTitle()).toBeVisible();

    // why is this needed? sometimes it scrolls to an invisible area where the panel is parked offscreen...
    await new MainMenu(this.page).scrollIntoView();
  }

  async expectClosed() {
    await expect(this._dialog).not.toBeAttached();
    await expect(this.getTitle()).not.toBeVisible();
  }

  protected override getArea(): Locator {
    return this.getDialog();
  }

  /** the dialog element itself. note that dialog elements have zero size, so they are never 'visible' - use getToolbar() to check visibility */
  getDialog() {
    return this._dialog;
  }

  getToolbar() {
    return this._dialog.locator('app-bd-dialog-toolbar');
  }

  getTitle() {
    return this.getToolbar().getByTestId('dialog-title');
  }

  getScope() {
    return this.getToolbar().locator('app-bd-current-scope');
  }

  async getLocalMessageDialog(title: string) {
    const locator = this.getDialog().locator('app-bd-dialog-message').locator('app-bd-notification-card');
    await expect(locator).toBeVisible();
    await expect(locator.locator('strong', { hasText: title })).toBeVisible();
    return Promise.resolve(locator);
  }
}
