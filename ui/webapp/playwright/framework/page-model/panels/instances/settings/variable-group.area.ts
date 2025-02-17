import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator, Page } from '@playwright/test';
import { expect } from '@bdeploy-setup';
import { CustomVariablePopup } from '@bdeploy-pom/panels/instances/settings/custom-variable.popup';

export class VariableGroupArea extends BaseArea {
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

  async addCustomVariable() {
    expect(this.name).toBe('Custom Variables');
    await this._group.locator('mat-expansion-panel-header').getByRole('button', { name: 'Add Custom Variable' }).click();
    return Promise.resolve(new CustomVariablePopup(this._group));
  }
}