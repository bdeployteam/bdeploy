import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';

export class ClientAppsPage extends BaseDialog {
  constructor(page: Page, private readonly group: string) {
    super(page, 'app-client-applications');
  }

  async goto() {
    const instanceBrowser = new InstancesBrowserPage(this.page, this.group);
    await instanceBrowser.goto();

    const mainMenu = new MainMenu(this.page);
    await mainMenu.getNavButton('Client Applications').click();

    await this.page.waitForURL(`/#/groups/clients/${this.group}`);
    await expect(this.getTitle()).toContainText('Client Applications');
  }
}