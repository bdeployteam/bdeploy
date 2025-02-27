import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { createPanel, createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { Page } from '@playwright/test';
import { GroupDetailsPanel } from '@bdeploy-pom/panels/admin/group-details.panel';
import { AddGroupPanel } from '@bdeploy-pom/panels/admin/add-group.panel';

export class UserGroupsPage extends BaseDialog {
  constructor(page: Page) {
    super(page, 'app-user-groups-browser');
  }

  async getGroupDetailsPanel(name: string) {
    return createPanelFromRow(this.getTableRowContaining(name), p => new GroupDetailsPanel(p));
  }

  async getAddGroupPanel() {
    return createPanel(this.getToolbar(), 'Create User Group...', p => new AddGroupPanel(p));
  }
}