import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { expect, Page } from '@playwright/test';
import { AppTemplateVarsPopup } from '@bdeploy-pom/panels/instances/settings/app-template-vars.popup';

export class AddProcessPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-add-process');
  }

  async addProcess(name: string) {
    await this.getDialog().getByRole('button', { name: `Add ${name} to selected node` }).click();
  }

  async addProcessTemplate(name: string) {
    await this.getTemplateRowButton(name).click();
  }

  async checkTemplateCanBeSelected(name: string) {
    await this.getTemplateRowButton(name).isEnabled();
  }

  async checkTemplateCannotBeSelected(name: string) {
    await this.getTemplateRowButton(name).isDisabled();
  }

  async shouldHaveOptionCount(nrOfOptions: number) {
    await expect(this.getDialog().getByRole('button', { name: /Add [a-zA-Z0-9 ]+ to selected node/i  })).toHaveCount(nrOfOptions);
  }

  getVariableTemplatePopup() {
    return new AppTemplateVarsPopup(this.getDialog());
  }

  private getTemplateRowButton(name: string) {
    return this.getDialog().getByRole('button', { name: `Add template ${name} to selected node` });
  }
}