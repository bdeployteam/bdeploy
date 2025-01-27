import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';

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

  async install() {
    const installBtn = this.getDialog().getByRole('button', { name: 'Install' });
    await installBtn.click();
    await expect(installBtn.locator('mat-spinner')).not.toBeVisible();
  }

  async activate() {
    const activateBtn = this.getDialog().getByRole('button', { name: 'Activate' });
    await activateBtn.click();
    await expect(activateBtn.locator('mat-spinner')).not.toBeVisible();
  }

  getServerNode(name: string) {
    return this.getDialog().locator('app-instance-server-node', { hasText: name });
  }

  getClientNode(name: string) {
    return this.getDialog().locator('app-instance-client-node');
  }
}