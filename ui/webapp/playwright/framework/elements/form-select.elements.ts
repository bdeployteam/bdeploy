import { Locator } from '@playwright/test';
import { BaseArea } from '@bdeploy-pom/base/base-area';
import { expect } from '@bdeploy-setup';

export class FormSelectElement extends BaseArea {
  private readonly _select: Locator;
  private readonly _trigger: Locator;
  private readonly _options: Locator;
  private readonly _value: Locator;

  constructor(private readonly container: Locator, label: string) {
    super(container.page());
    const parent = container.locator('mat-form-field').filter({ has: container.page().getByText(label, { exact: true }) });
    this._select = parent.getByRole('combobox');
    this._trigger = this._select.locator('.mat-mdc-select-trigger');
    this._options = this.getOverlayContainer().getByRole('listbox').locator('mat-option');
    this._value = this._trigger.locator('.mat-mdc-select-value');
  }

  protected override getArea(): Locator {
    return this._select;
  }

  getValue() {
    return this._value;
  }

  async selectOption(option: string, exact: boolean = false) {
    await expect(this._select).toBeVisible();

    await this._trigger.click();
    await this._options.getByText(option, { exact: exact }).click();
  }

  getOptions() {
    return this._options;
  }

  getTrigger() {
    return this._trigger;
  }

}