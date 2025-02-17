import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

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
}