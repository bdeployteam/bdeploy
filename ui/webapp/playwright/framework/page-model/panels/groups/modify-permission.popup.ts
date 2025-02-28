import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class ModifyPermissionPopup extends BaseArea {
  private readonly _dialog: Locator;

  constructor(parent: Locator) {
    super(parent.page());

    this._dialog = parent.locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Modify' });
  }

  protected getArea(): Locator {
    return this._dialog;
  }

  async selectPermission(permission: string) {
    await new FormSelectElement(this._dialog, 'Modify Permission').selectOption(permission);
  }

  async ok() {
    await this._dialog.locator('button', { hasText: 'OK' }).click();
  }

  async cancel() {
    await this._dialog.locator('button', { hasText: 'Cancel' }).click();
  }

  async remove() {
    await this._dialog.locator('button', { hasText: 'Remove' }).click();
  }
}