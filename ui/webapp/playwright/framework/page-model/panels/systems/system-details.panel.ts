import { Page } from '@playwright/test';
import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { expect } from '@bdeploy-setup';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { SystemVariablesPanel } from './system-variables.panel';

export class SystemDetailsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-system-details');
  }

  async getSystemVariablePanel() {
    return createPanel(this.getDialog(), 'System Variables...', p => new SystemVariablesPanel(p));
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
    await expect(this.getDialog()).not.toBeAttached();
  }
}
