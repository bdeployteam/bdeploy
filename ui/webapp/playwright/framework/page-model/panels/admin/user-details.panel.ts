import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { AssignPermissionPanel } from '@bdeploy-pom/panels/admin/assign-permission.panel';

export class UserDetailsPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-user-admin-detail');
  }

  async getAssignPermissionPanel() {
    return createPanel(this.getDialog(), 'Assign Permission...', p => new AssignPermissionPanel(p));
  }
}