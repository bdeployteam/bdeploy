import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';

export class AppTemplateVarsPopup extends BaseArea {
  constructor(private readonly _parent: Locator) {
    super(_parent.page());
  }

  protected getArea(): Locator {
    return this._parent.locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Assign Variable Values' });
  }

  fillTextVariable(name: string, value: string) {
    return this.getArea().getByLabel(name).fill(value);
  }

  async confirm() {
    await this.getArea().getByRole('button', { name: 'Confirm' }).click();
  }

  async cancel() {
    await this.getArea().getByRole('button', { name: 'Cancel' }).click();
  }
}