import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { RepoPermissionsPanel } from '@bdeploy-pom/panels/repos/repo-permissions.panel';

export class RepoSettingsPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-settings');
  }

  async getRepoPermissionsPanel() {
    return createPanel(this.getDialog(), 'Software Repository Permissions', p => new RepoPermissionsPanel(p));
  }
}