import { BasePermissionPanel } from '@bdeploy-pom/panels/base-permission.panel';
import { Page } from '@playwright/test';
import { ModifyPermissionPopup } from '@bdeploy-pom/panels/groups/modify-permission.popup';

export class InstanceGroupPermissionsPanel extends BasePermissionPanel {
  constructor(page: Page) {
    super(page, 'app-instance-group-permissions');
  }

  async getModifyPopup(user: string) {
    await this.getTableRowContaining(user).getByRole('button', { name: 'Modify' }).click();
    return Promise.resolve(new ModifyPermissionPopup(this.getDialog()));
  }
}