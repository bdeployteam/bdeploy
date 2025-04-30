import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { VariableGroupArea } from '@bdeploy-pom/panels/instances/settings/variable-group.area';
import { expect, Page } from '@playwright/test';

export class InstanceVariablePanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-instance-variables');
  }

  async getVariableGroup(name: string) {
    return new VariableGroupArea(this.page, this.getDialog(), name);
  }

  async apply() {
    await this.getToolbar().getByRole('button', { name: 'Apply' }).click();
    await expect(this.getDialog()).not.toBeAttached();
  }
}