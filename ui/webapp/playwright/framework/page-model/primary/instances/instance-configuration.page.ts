import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { createPanel, createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { InstanceSettingsPanel } from '@bdeploy-pom/panels/instances/instance-settings.panel';
import { AddProcessPanel } from '@bdeploy-pom/panels/instances/add-process.panel';
import { LocalChangesPanel } from '@bdeploy-pom/panels/instances/local-changes.panel';
import { ProcessSettingsPanel } from '@bdeploy-pom/panels/instances/process-settings.panel';

export class InstanceConfigurationPage extends BaseDialog {
  constructor(page: Page, private readonly group: string, private readonly instance: string) {
    super(page, 'app-configuration');
  }

  async goto() {
    const instBrowser = new InstanceDashboardPage(this.page, this.group, this.instance);
    await instBrowser.goto();

    await new MainMenu(this.page).getNavButton('Instance Configuration').click();
    await this.page.waitForURL(`/#/instances/configuration/${this.group}/**`);
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Configuration');
    await expect(this.getScope()).toContainText(this.instance);
  }

  async save() {
    await this.getToolbar().getByRole('button', { name: 'Save Changes' }).click();
    await this.page.waitForURL(`/#/instances/dashboard/${this.group}/**`);
  }

  async waitForValidation() {
    await expect(this.getToolbar().getByRole('button', { name: 'Save Changes' }).locator('mat-spinner')).not.toBeVisible();
  }

  async undo() {
    await this.getToolbar().getByRole('button', { name: 'Undo' }).click();
  }

  async redo() {
    await this.getToolbar().getByRole('button', { name: 'Redo' }).click();
  }

  async getSettingsPanel() {
    const panel = new InstanceSettingsPanel(this.page);
    // check for the toolbar - the dialog is always "invisible", as it has a zero size
    if(await panel.getToolbar().isVisible()) {
      return Promise.resolve(panel);
    }

    return createPanel(this.getToolbar(), 'Instance Settings', (p) => panel);
  }

  async getAddProcessPanel(node: string) {
    return createPanel(this.getConfigNode(node), 'Add Process Configuration', (p) => new AddProcessPanel(p));
  }

  async getLocalChangesPanel() {
    return createPanel(this.getToolbar(), 'Local Changes', (p) => new LocalChangesPanel(p));
  }

  async getProcessSettingsPanel(node: string, process: string, nth: number = 0) {
    return createPanelFromRow(this.getConfigNode(node).getByRole('row', { name: process }).nth(nth), p => new ProcessSettingsPanel(p));
  }

  /** get the node configuration container. client application node uses __ClientApplications as name */
  getConfigNode(name: string) {
    return this.getDialog().getByTestId(name);
  }

  getBanner() {
    return this.getDialog().locator('app-bd-banner');
  }

}