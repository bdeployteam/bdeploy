import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class CustomVariablePopup extends BaseArea {
  private readonly _dialog: Locator;

  constructor(parent: Locator) {
    super(parent.page());

    this._dialog = parent.page().locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Add Variable' });
  }

  protected getArea(): Locator {
    return this._dialog;
  }

  async fill(id: string, value: string, description?: string, type?: string, editor?: string) {
    await this._dialog.getByLabel('Variable ID').fill(id);
    await this._dialog.locator('app-bd-form-input').getByLabel('Value').fill(value);
    if (description) {
      await this._dialog.getByLabel('Description').fill(description);
    }
    if (type) {
      await new FormSelectElement(this._dialog, 'Value Type').selectOption(type);
    }
    if (editor) {
      await new FormSelectElement(this._dialog, 'Custom Editor (Plugin)').selectOption(editor);
    }
  }

  async fillLink(value: string) {
    const input = this._dialog.locator('app-bd-form-input', { hasText: 'Value' });
    await input.getByRole('radio').nth(1).click();
    await input.locator('input').fill(value);
  }

  async ok() {
    await this._dialog.locator('button', { hasText: 'OK' }).click();
  }

  async cancel() {
    await this._dialog.locator('button', { hasText: 'Cancel' }).click();
  }
}