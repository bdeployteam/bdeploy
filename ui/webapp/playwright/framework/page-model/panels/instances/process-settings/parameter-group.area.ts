import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator, Page } from '@playwright/test';
import { expect } from '@bdeploy-setup';
import { CustomParameterPopup } from '@bdeploy-pom/panels/instances/process-settings/custom-parameter.popup';

export class ParameterGroupArea extends BaseArea {
  private readonly _group: Locator;

  constructor(page: Page, parent: Locator, private readonly name: string) {
    super(page);

    this._group = parent.locator('mat-expansion-panel', { has: page.locator('mat-expansion-panel-header', { hasText: name }) });
  }

  protected getArea(): Locator {
    return this._group;
  }

  async toggle() {
    await this._group.locator('mat-expansion-panel-header').click();
  }

  async selectParameters() {
    await this._group.locator('mat-expansion-panel-header').getByRole('button', { name: 'Select Parameters' }).click();
    await expect(this._group.getByText('Mandatory parameters cannot be removed')).toBeVisible();
  }

  async finishSelectParameters() {
    await this._group.locator('mat-expansion-panel-header').getByRole('button', { name: 'Confirm Selection' }).click();
    await expect(this._group.getByText('Mandatory parameters cannot be removed')).not.toBeVisible();
  }

  getParameter(id: string) {
    return this._group.getByTestId(id);
  }

  async addCustomParameter() {
    expect(this.name).toBe('Custom Parameters');
    await this._group.locator('mat-expansion-panel-header').getByRole('button', { name: 'Add Custom Parameter' }).click();
    return Promise.resolve(new CustomParameterPopup(this._group));
  }

  async parameterHasValue(id: string, expectedValue: string) {
    await expect(this.getParameter(id).locator("input").nth(0)).toHaveValue(expectedValue);
  }

  async shouldBeSelected(id: string) {
    await expect(this.getParameter(id).getByTestId('toggle-param')).toContainText('delete');
  }

  async shouldNotBeSelected(id: string) {
    await expect(this.getParameter(id).getByTestId('toggle-param')).toContainText('add');
  }

  async toggleParameter(id: string) {
    await this.getParameter(id).getByTestId('toggle-param').click();
  }

}