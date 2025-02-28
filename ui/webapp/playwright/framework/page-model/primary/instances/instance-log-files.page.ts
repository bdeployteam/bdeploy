import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { ViewFilePanel } from '@bdeploy-pom/panels/instances/view-file.panel';

export class InstanceLogFilesPage extends BaseDialog {
  constructor(page: Page, private readonly group: string, private readonly instance: string) {
    super(page, 'app-files-display');
  }

  async goto() {
    const instBrowser = new InstanceDashboardPage(this.page, this.group, this.instance);
    await instBrowser.goto();

    await new MainMenu(this.page).getNavButton('Log Files').click();
    await this.page.waitForURL(`/#/instances/log-files/${this.group}/**`);
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Log Files');
    await expect(this.getScope()).toContainText(this.instance);
  }

  async getFileViewer(filename: string) {
    return createPanelFromRow(this.getTableRowContaining(filename), p => new ViewFilePanel(p));
  }
}