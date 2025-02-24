import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { VariableGroupArea } from '@bdeploy-pom/panels/instances/settings/variable-group.area';
import { expect, Page } from '@playwright/test';

export class SystemVariablesPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-system-variables');
  }

  async getVariableGroup(name: string) {
    return new VariableGroupArea(this.page, this.getDialog(), name);
  }

  async save(confirm = true) {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
    if (confirm) {
      const dlg = await this.getLocalMessageDialog('Saving');
      await dlg.getByRole('button', { name: 'Yes' }).click();
    }
    await expect(this.getDialog()).not.toBeAttached();
  }
}