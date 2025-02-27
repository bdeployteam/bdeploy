import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { AssignPermissionPanel } from '@bdeploy-pom/panels/admin/assign-permission.panel';
import { EditUserPanel } from '@bdeploy-pom/panels/admin/edit-user.panel';
import { expect } from '@bdeploy-setup';

export class UserDetailsPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-user-admin-detail');
  }

  async getAssignPermissionPanel() {
    return createPanel(this.getDialog(), 'Assign Permission...', p => new AssignPermissionPanel(p));
  }

  async deactivate() {
    await this.getDialog().getByRole('button', { name: 'Deactivate Account' }).click();
  }

  async activate() {
    await this.getDialog().getByRole('button', { name: 'Activate Account' }).click();
  }

  async delete() {
    await this.getDialog().getByRole('button', { name: 'Delete User' }).click();
    const dlg = await this.getLocalMessageDialog('Delete User');
    await expect(dlg).toBeVisible();
    await dlg.getByRole('button', { name: 'Yes' }).click();
    await this.expectClosed();
  }

  async getEditPanel() {
    return createPanel(this.getDialog(), 'Edit User...', p => new EditUserPanel(p));
  }
}