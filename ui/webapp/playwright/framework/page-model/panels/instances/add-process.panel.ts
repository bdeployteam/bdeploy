import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { AppTemplateVarsPopup } from '@bdeploy-pom/panels/instances/settings/app-template-vars.popup';

export class AddProcessPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-add-process');
  }

  async addProcess(name: string) {
    await this.getDialog().getByRole('button', { name: `Add ${name} to selected node` }).click();
  }

  async addProcessTemplate(name: string) {
    await this.getDialog().getByRole('button', { name: `Add template ${name} to selected node` }).click();
  }

  getVariableTemplatePopup() {
    return new AppTemplateVarsPopup(this.getDialog());
  }
}