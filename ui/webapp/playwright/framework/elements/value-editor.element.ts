import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';
import { expect } from '@bdeploy-setup';

export class ValueEditorElement extends BaseArea {
  private readonly _container: Locator;
  private readonly _formElement: Locator;

  constructor(parent: Locator, label: string) {
    super(parent.page());
    // label changes based on type of value picked, so I need to use or to be able to reuse this wrapper class
    this._container = parent.locator('app-bd-value-editor').filter( {
      has: parent.page().getByText(label, { exact: true }).or(parent.page().getByText(label + ' (Link Expression)', { exact: true }))
    });
    this._formElement = this._container.locator('input');
  }

  protected getArea(): Locator {
    throw this._container;
  }

  async shouldHaveValueAndPreview(value: string, preview: string) {
    await this.shouldHaveValue(value);
    await this.hasPreview(preview);
  }

  async shouldBeEmpty() {
    await this.shouldHaveValue('')
  }

  async shouldHaveValue(value: string) {
    await expect(this._formElement).toHaveValue(value);
  }

  async hasPreview(preview: string) {
    await this._formElement.click();
    await expect(this._container.getByText('Preview')).toContainText(preview);
  }

  async shouldBeDisabled() {
    await expect(this._formElement).toBeDisabled();
  }

  async shouldNotExist() {
    await expect(this._container).toHaveCount(0);
  }

  async toggle() {
    await this._container.scrollIntoViewIfNeeded();
    await this._container.click();
  }

  async setValue(value: string) {
    await this._container.locator('mat-button-toggle').nth(0).click();
    await this._formElement.clear();
    await this._formElement.fill(value);
  }

  async selectValue(option: string) {
    await this._container.locator('mat-button-toggle').nth(0).click();
    await this._formElement.clear();

    await this._formElement.click();
    await this.getOverlayContainer().getByRole('listbox').locator('mat-option')
      .filter({ hasText: option }).click();
  }

  async setLinkExpression(value: string) {
    await this._container.locator('mat-button-toggle').nth(1).click();
    await this._formElement.fill(value);
  }
}