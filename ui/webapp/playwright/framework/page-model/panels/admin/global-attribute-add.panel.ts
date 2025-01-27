import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Locator, Page } from '@playwright/test';
import { expect } from '@bdeploy-setup';

export class GlobalAttributeAddPanel extends BasePanel {
  private readonly _saveButton: Locator;

  constructor(page: Page) {
    super(page, 'app-add-global-attribute');
    this._saveButton = this.getDialog().getByRole('button', { name: 'Save' });
  }

  async fill(name: string, description: string) {
    await expect(this._saveButton).toBeDisabled();

    await this.getDialog().getByLabel('Name').fill(name);
    await this.getDialog().getByLabel('Description').fill(description);

    await expect(this._saveButton).toBeEnabled();
  }

  async save() {
    await this._saveButton.click();
    await expect(this.getDialog()).not.toBeAttached();
  }
}