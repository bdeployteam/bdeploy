import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { ReportRowDetailPanel } from '@bdeploy-pom/panels/reports/report-row-detail.panel';

export class ReportResultPage extends BaseDialog {
  constructor(page: any) {
    super(page, 'app-report');
  }

  async getReportRowDetail(name: string) {
    return createPanelFromRow(this.getTableRowContaining(name).first(), p => new ReportRowDetailPanel(p));
  }
}