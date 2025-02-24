import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { HistoryEntryDetailsPanel } from '@bdeploy-pom/panels/instances/history/history-entry-details.panel';

export class InstanceHistoryPage extends BaseDialog {
  constructor(page: Page, private readonly group: string, private readonly instance: string) {
    super(page, 'app-history');
  }

  async goto() {
    const instBrowser = new InstanceDashboardPage(this.page, this.group, this.instance);
    await instBrowser.goto();

    await new MainMenu(this.page).getNavButton('Instance History').click();
    await this.page.waitForURL(`/#/instances/history/${this.group}/**`);
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('History');
    await expect(this.getScope()).toContainText(this.instance);
  }

  async toggleCreation() {
    await this.getToolbar().getByRole('button', { name: 'Show Creation Events' }).click();
  }

  async toggleDeployment() {
    await this.getToolbar().getByRole('button', { name: 'Show Deployment Events' }).click();
  }

  async toggleRuntime() {
    await this.getToolbar().getByRole('button', { name: 'Show Runtime Events' }).click();
  }

  async getEntryDetailPanel(entry: string) {
    return createPanelFromRow(this.getTableRowContaining(entry), p => new HistoryEntryDetailsPanel(p));
  }
}