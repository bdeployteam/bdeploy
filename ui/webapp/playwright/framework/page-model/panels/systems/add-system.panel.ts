import { Page } from '@playwright/test';
import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { expect } from '@bdeploy-setup';

export class AddSystemPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-add-system');
  }

  async fill(name: string, description: string) {
    await this.getDialog().getByLabel('System Name').fill(name);
    await this.getDialog().getByLabel('Description').fill(description);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
    await expect(this.getDialog()).not.toBeAttached();
  }
}
