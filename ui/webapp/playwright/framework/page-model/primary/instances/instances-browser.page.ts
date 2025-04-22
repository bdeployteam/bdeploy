import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstanceGroupsBrowserPage } from '@bdeploy-pom/primary/groups/groups-browser.page';
import { InstanceGroupSettingsPanel } from '@bdeploy-pom/panels/groups/instance-group-settings.panel';
import { AddInstancePanel } from '@bdeploy-pom/panels/instances/add-instance.panel';
import { BulkInstancesPanel } from '@bdeploy-pom/panels/instances/bulk-instances.panel';
import { createPanel, waitForInstanceGroup } from '@bdeploy-pom/common/common-functions';

export class InstancesBrowserPage extends BaseDialog {
  constructor(page: Page, private readonly group: string) {
    super(page, 'app-instances-browser');
  }

  async goto() {
    const groupBrowser = new InstanceGroupsBrowserPage(this.page);
    await groupBrowser.goto();

    await waitForInstanceGroup(groupBrowser, this.group);

    await groupBrowser.getTableRowContaining(this.group).click();
    await this.page.waitForURL(`/#/instances/browser/${this.group}`);
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Instances');
  }

  async getGroupSettings() {
    return createPanel(this.getToolbar(), 'Group Settings', (p) => new InstanceGroupSettingsPanel(p));
  }

  async addInstance() {
    return createPanel(this.getToolbar(), 'Add Instance', (p) => new AddInstancePanel(p));
  }

  async bulkManipulation() {
    return createPanel(this.getToolbar(), 'Bulk Manipulation', (p) => new BulkInstancesPanel(p));
  }

  async syncAll() {
    const syncButton = this.getToolbar().getByRole('button', { name: 'Fetch instance states' });
    await syncButton.click();

    const popup = this.getDialog().locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Query all Instances' });
    await expect(popup).toBeVisible();
    await popup.getByRole('button', { name: 'Yes' }).click();

    await expect(syncButton.locator('mat-spinner')).not.toBeVisible();
  }

  shouldHaveTableRows(nrOfExpectedRows: number) {
    return expect(this.getArea().locator('tbody').getByRole('row')).toHaveCount(nrOfExpectedRows);
  }
}