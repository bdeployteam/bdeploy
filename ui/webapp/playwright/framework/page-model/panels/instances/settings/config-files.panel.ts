import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { FileEditorPanel } from '@bdeploy-pom/panels/instances/file-editor.panel';

export class ConfigFilesPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-config-files');
  }

  async addFile(name: string) {
    await this.getToolbar().getByLabel('Add File').click();
    const dlg = this.getDialog().locator('app-bd-dialog-message').locator('app-bd-notification-card');
    await dlg.getByLabel('File Name').fill(name);
    await dlg.getByRole('button', { name: 'OK' }).click();
  }

  async editFile(name: string) {
    await this.getTableRowContaining(name).hover();
    return createPanel(this.getTableRowContaining(name), 'Edit', p => new FileEditorPanel(p));
  }
}