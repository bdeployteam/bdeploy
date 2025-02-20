import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';

export class LaunchConfirmationPopup extends BaseArea {
  constructor(private readonly _parent: Locator) {
    super(_parent.page());
  }

  protected getArea(): Locator {
    return this._parent.locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Confirm Process Start' });
  }

  async fill(name: string) {
    await this.getArea().getByLabel('Confirm using process name').fill(name);
  }

  async ok() {
    await this.getArea().getByRole('button', { name: 'OK' }).click();
  }

  async cancel() {
    await this.getArea().getByRole('button', { name: 'Cancel' }).click();
  }
}