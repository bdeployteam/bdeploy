import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { ProcessStatusPanel } from '@bdeploy-pom/panels/instances/process-status.panel';
import { InstallationConfirmationPopup } from '@bdeploy-pom/panels/instances/installation-confirmation.popup';
import { MultiNodeProcessStatusPanel } from '@bdeploy-pom/panels/instances/multi-node-process-status.panel';

export class InstanceDashboardPage extends BaseDialog {
  constructor(page: Page, private readonly group: string, private readonly instance: string) {
    super(page, 'app-dashboard');
  }

  async goto() {
    const instBrowser = new InstancesBrowserPage(this.page, this.group);
    await instBrowser.goto();
    await instBrowser.getTableRowContaining(this.instance).click();
    await this.page.waitForURL(`/#/instances/dashboard/${this.group}/**`);
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Dashboard');
    await expect(this.getScope()).toContainText(this.instance);
  }

  getInstallButton() {
    return this.getDialog().getByRole('button', { name: 'Install' });
  }

  getActivateButton() {
    return this.getDialog().getByRole('button', { name: 'Activate' });
  }

  async install(expectedVersion: string) {
    // need to check for the expected version to be on screen as this will make sure that the
    // expected version has already loaded from the backend!
    await expect(this.getDialog().locator('.bd-rect-card').getByText(`(version ${expectedVersion})`)).toBeVisible();
    const installBtn = this.getInstallButton();
    await installBtn.click();
    await installBtn.locator('mat-spinner').waitFor({ state: 'detached' });
    await expect(installBtn.locator('mat-spinner')).not.toBeVisible();
  }

  async activate() {
    const activateBtn = this.getActivateButton();
    await activateBtn.click();
    await expect(activateBtn.locator('mat-spinner')).not.toBeVisible();
  }

  async activateAndExpectConfirmationDialog() {
    const activateBtn = this.getActivateButton();
    await activateBtn.click();
    return this.getConfirmationPopup();
  }

  async getProcessStatus(node: string, process: string) {
    return createPanelFromRow(this.getNode(node).getByRole('row', { name: process }), (p) => new ProcessStatusPanel(p));
  }

  async getMultiNodeProcessStatus(node: string, process: string) {
    return createPanelFromRow(this.getNode(node).getByRole('row', { name: process }), (p) => new MultiNodeProcessStatusPanel(p));
  }

  async toggleBulkControl() {
    await this.getToolbar().getByRole('button', { name: 'Bulk Control' }).click();
  }

  async synchronize() {
    const syncButton = this.getToolbar().getByRole('button', { name: 'Synchronize' });
    await syncButton.click();
    await expect(syncButton.locator('mat-spinner')).not.toBeVisible();
  }

  getServerNode(name: string) {
    return this.getDialog().locator('app-instance-server-node', { hasText: name });
  }

  getNode(name: string) {
    return this.getDialog().locator('app-instance-server-node, app-instance-multi-node', { hasText: name });
  }

  getClientNode(name: string) {
    return this.getDialog().locator('app-instance-client-node');
  }

  getConfirmationPopup() {
    return new InstallationConfirmationPopup(this.getDialog());
  }

  async shouldHaveServerNodeCount(expectedNrOfNodes: number) {
    await expect(this.getDialog().locator('app-instance-server-node')).toHaveCount(expectedNrOfNodes);
  }

  async shouldHaveMultiNodeCount(expectedNrOfNodes: number) {
    await expect(this.getDialog().locator('app-instance-multi-node')).toHaveCount(expectedNrOfNodes);
  }

  async shouldHaveProcessCountForNode(nodeTestId: string, expectedNrOfProcesses: number) {
    await expect(this.getNode(nodeTestId).locator('tr:has(td.cdk-column-id)'))
      .toHaveCount(expectedNrOfProcesses);
  }

}