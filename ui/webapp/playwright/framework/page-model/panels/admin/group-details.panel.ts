import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { AssignPermissionPanel } from '@bdeploy-pom/panels/admin/assign-permission.panel';
import { expect } from '@bdeploy-setup';
import { EditGroupPanel } from '@bdeploy-pom/panels/admin/edit-group.panel';

export class GroupDetailsPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-user-group-admin-detail');
  }

  async getAssignPermissionPanel() {
    return createPanel(this.getDialog(), 'Assign Permission...', p => new AssignPermissionPanel(p, true));
  }

  async fillAddUser(user: string) {
    await this.getDialog().getByLabel('Add user to the group').fill(user);
  }

  async delete() {
    await this.getDialog().getByRole('button', { name: 'Delete Group' }).click();
    const dlg = await this.getLocalMessageDialog('Delete User Group');
    await expect(dlg).toBeVisible();
    await dlg.getByRole('button', { name: 'Yes' }).click();
    await this.expectClosed();
  }

  async getEditPanel() {
    return createPanel(this.getDialog(), 'Edit Group...', p => new EditGroupPanel(p));
  }
}