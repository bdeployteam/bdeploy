import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstanceGroupsBrowserPage } from '@bdeploy-pom/primary/groups/groups-browser.page';
import { createPanel, waitForInstanceGroup } from '@bdeploy-pom/common/common-functions';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { LinkManagedServerPanel } from '@bdeploy-pom/panels/groups/link-managed-server.panel';

export class ManagedServersPage extends BaseDialog {
  constructor(page: Page, private readonly group: string) {
    super(page, 'app-servers-browser');
  }

  async goto() {
    const groupBrowser = new InstanceGroupsBrowserPage(this.page);
    await groupBrowser.goto();

    await waitForInstanceGroup(groupBrowser, this.group);

    await groupBrowser.getTableRowContaining(this.group).click();
    await this.page.waitForURL(`/#/instances/browser/${this.group}`);

    await new MainMenu(this.page).getNavButton('Managed Servers').click();

    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Managed Servers');
  }

  async getLinkServerPanel() {
    return createPanel(this.getToolbar(), 'Link Managed Server', p => new LinkManagedServerPanel(p));
  }
}