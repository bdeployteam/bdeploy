import { Locator, Page } from '@playwright/test';
import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { expect } from '@bdeploy-setup';
import * as path from 'node:path';
import { FormCheckboxElement } from '@bdeploy-elements/form-checkbox.element';

export class AddInstanceGroupPanel extends BasePanel {
  private readonly _id: Locator;
  private readonly _title: Locator;
  private readonly _description: Locator;
  private readonly _autoCleanup: FormCheckboxElement;
  private readonly _saveButton: Locator;

  constructor(page: Page) {
    super(page, 'app-add-group');

    this._id = this.getDialog().getByLabel('Group ID');
    this._title = this.getDialog().getByLabel('Title');
    this._description = this.getDialog().getByLabel('Description');
    this._autoCleanup = new FormCheckboxElement(this.getDialog(), 'Automatic Cleanup');
    this._saveButton = this.getDialog().getByRole('button', { name: 'Save' });
  }

  async fill(id: string, title: string, description: string, autoCleanup: boolean, image?: string) {
    await this._id.fill(id);
    await this._title.fill(title);
    await this._description.fill(description);
    await this._autoCleanup.setChecked(autoCleanup);

    if (image) {
      const chooserEvent = this.page.waitForEvent('filechooser');
      await this.getDialog().locator('div', { hasText: 'Logo' }).getByLabel('Browse').click();

      const chooser = await chooserEvent;
      await chooser.setFiles(path.join('playwright', 'fixtures', image));
    }
  }

  async save() {
    await this._saveButton.click();
    await expect(this.getDialog()).not.toBeAttached();
  }
}
