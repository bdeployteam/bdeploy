import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { BHiveDetailsPanel } from '@bdeploy-pom/panels/admin/bhive-details.panel';

export class BHivesPage extends BaseDialog {
  constructor(page: any) {
    super(page, 'app-bhive');
  }

  async getHiveDetails(name: string) {
    return createPanelFromRow(this.getTableRowContaining(name), p => new BHiveDetailsPanel(p));
  }
}