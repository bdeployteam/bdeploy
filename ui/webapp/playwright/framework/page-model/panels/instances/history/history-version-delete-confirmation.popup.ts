import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';

export class HistoryVersionDeleteConfirmationPopup extends BaseArea {
  constructor(private readonly _parent: Locator) {
    super(_parent.page());
  }

  protected getArea(): Locator {
    return this._parent.locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Delete Version' });
  }

  async fill(confirmation: string) {
    await this.getArea().getByLabel('Enter confirmation').fill(confirmation);
  }

  async yes() {
    await this.getArea().getByRole('button', { name: 'Yes' }).click();
  }

  async no() {
    await this.getArea().getByRole('button', { name: 'No' }).click();
  }
}