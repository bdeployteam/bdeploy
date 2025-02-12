import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class CustomParameterPopup extends BaseArea {
  private readonly _dialog: Locator;

  constructor(parent: Locator) {
    super(parent.page());

    this._dialog = parent.page().locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Add Custom Parameter' });
  }

  protected getArea(): Locator {
    return this._dialog;
  }

  async fill(id: string, value: string, predecessor?: string) {
    await this._dialog.getByLabel('Unique ID').fill(id);
    if (predecessor) {
      await new FormSelectElement(this._dialog, 'Predecessor').selectOption(predecessor);
    }
    await this._dialog.getByLabel('Value').fill(value);
  }

  async ok() {
    await this._dialog.locator('button', { hasText: 'OK' }).click();
  }

  async cancel() {
    await this._dialog.locator('button', { hasText: 'Cancel' }).click();
  }
}