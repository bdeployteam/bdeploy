import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { Page } from '@playwright/test';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { ReportFormPanel } from '@bdeploy-pom/panels/reports/report-form.panel';

export class ReportsBrowserPage extends BaseDialog {
  constructor(page: Page) {
    super(page, 'app-reports-browser');
  }

  async goto() {
    await new MainMenu(this.page).getNavButton('Reports').click();
    await this.page.waitForURL('/#/reports/browser');
    await this.expectOpen();
  }

  async getReportFormPanel(name: string) {
    return createPanelFromRow(this.getTableRowContaining(name), p => new ReportFormPanel(p));
  }
}