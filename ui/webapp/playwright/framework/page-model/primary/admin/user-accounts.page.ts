import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { createPanel, createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { UserDetailsPanel } from '@bdeploy-pom/panels/admin/user-details.panel';
import { Page } from '@playwright/test';
import { AddUserPanel } from '@bdeploy-pom/panels/admin/add-user.panel';

export class UserAccountsPage extends BaseDialog {
  constructor(page: Page) {
    super(page, 'app-users-browser');
  }

  async getUserDetailsPanel(name: string) {
    return createPanelFromRow(this.getTableRowContaining(name), p => new UserDetailsPanel(p));
  }

  async getAddUserPanel() {
    return createPanel(this.getToolbar(), 'Create User...', p => new AddUserPanel(p));
  }
}