import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Locator, Page } from '@playwright/test';
import { expect } from '@bdeploy-setup';

export class GlobalAttributeAddPanel extends BasePanel {
  private readonly _applyButton: Locator;

  constructor(page: Page) {
    super(page, 'app-add-global-attribute');
    this._applyButton = this.getDialog().getByRole('button', { name: 'Apply' });
  }

  async fill(name: string, description: string) {
    await expect(this._applyButton).toBeDisabled();

    await this.getDialog().getByLabel('Name').fill(name);
    await this.getDialog().getByLabel('Description').fill(description);

    await expect(this._applyButton).toBeEnabled();
  }

  async apply() {
    await this._applyButton.click();
    await expect(this.getDialog()).not.toBeAttached();
  }
}