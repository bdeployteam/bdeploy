import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { UserDetailsPanel } from '@bdeploy-pom/panels/admin/user-details.panel';

export class UserAccountsPage extends BaseDialog {
  constructor(page: any) {
    super(page, 'app-users-browser');
  }

  async getUserDetails(name: string) {
    return createPanelFromRow(this.getTableRowContaining(name), p => new UserDetailsPanel(p));
  }
}