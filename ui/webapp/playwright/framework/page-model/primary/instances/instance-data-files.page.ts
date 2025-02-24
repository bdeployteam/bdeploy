import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { createPanel, createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { AddDataFilePanel } from '@bdeploy-pom/panels/instances/data-files/add-data-file.panel';
import { ViewDataFilePanel } from '@bdeploy-pom/panels/instances/data-files/view-data-file.panel';

export class InstanceDataFilesPage extends BaseDialog {
  constructor(page: Page, private readonly group: string, private readonly instance: string) {
    super(page, 'app-files-display');
  }

  async goto() {
    const instBrowser = new InstanceDashboardPage(this.page, this.group, this.instance);
    await instBrowser.goto();

    await new MainMenu(this.page).getNavButton('Data Files').click();
    await this.page.waitForURL(`/#/instances/data-files/${this.group}/**`);
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Data Files');
    await expect(this.getScope()).toContainText(this.instance);
  }

  async getAddFilePanel() {
    return createPanel(this.getToolbar(), 'Add File...', p => new AddDataFilePanel(p));
  }

  async getFileViewer(filename: string) {
    return createPanelFromRow(this.getTableRowContaining(filename), p => new ViewDataFilePanel(p));
  }
}