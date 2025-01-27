import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';
import { expect } from '@bdeploy-setup';
import { FormCheckboxElement } from '@bdeploy-elements/form-checkbox.element';

export class InstanceTemplatesPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-instance-templates');
  }

  private async checkStep(step: number) {
    await expect(this.getDialog().locator('.mat-step-icon-selected', { hasText: step.toString() })).toBeVisible();
  }

  async selectTemplate(name: string) {
    await this.checkStep(1);
    await new FormSelectElement(this.getDialog(), 'Template').selectOption(name);
    await this.checkStep(2);
  }

  async selectGroup(groupName: string, node: string) {
    await this.checkStep(2);
    await new FormSelectElement(this.getDialog(), `Group '${groupName}'`).selectOption(`Apply to ${node}`);
  }

  async finishGroupSelection() {
    await this.checkStep(2);
    await this.getDialog().getByRole('button', { name: 'Next' }).click();
    await this.checkStep(3);
  }

  async fillLiteralVariable(name: string, value: string) {
    await this.getDialog().getByLabel(name).fill(value);
  }

  async fillBooleanVariable(name: string, value: boolean) {
    await new FormCheckboxElement(this.getDialog(), name).setChecked(value);
  }

  async finishTemplate() {
    await this.getDialog().getByRole('button', { name: 'Confirm' }).click();
  }
}