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

  async createCustomVariable(id: string, value: string, description?: string, type?: string, editor?: string) {
    const customVarDialog = await this.addCustomVariable();
    await customVarDialog.fill(id, value, description, type, editor);
    if (value.startsWith('{{')) {
      await customVarDialog.fillLink(value);
    }
    await customVarDialog.ok();
  }

  async checkPreview(name: string, preview: string) {
    await this.getArea().getByRole('textbox', { name }).click();
    await expect(this.getArea().getByText('Preview')).toContainText(preview);
  }
}