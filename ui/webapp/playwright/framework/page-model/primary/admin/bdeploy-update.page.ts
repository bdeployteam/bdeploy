import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { UpdateDetailPanel } from '@bdeploy-pom/panels/admin/update-detail.panel';

export class BDeployUpdatePage extends BaseDialog {
  constructor(page: any) {
    super(page, 'app-update-browser');
  }

  async getUpdateDetailPanel(which: string) {
    return createPanelFromRow(this.getTableRowContaining(which), p => new UpdateDetailPanel(p));
  }
}