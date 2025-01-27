import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';

export class FormCheckboxElement extends BaseArea {
  private readonly _checkbox: Locator;
  private readonly _formElement: Locator;

  constructor(parent: Locator, label: string) {
    super(parent.page());
    this._formElement = parent.locator('app-bd-form-toggle', { hasText: label });
    this._checkbox = this._formElement.locator('input');
  }

  protected override getArea(): Locator {
    return this._formElement;
  }

  async check() {
    await this._checkbox.check({ force: true });
  }

  async uncheck() {
    await this._checkbox.uncheck({ force: true });
  }

  async setChecked(checked: boolean) {
    if (checked) {
      await this.check();
    } else {
      await this.uncheck();
    }
  }

}