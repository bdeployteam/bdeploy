import { expect, Page } from '@playwright/test';
import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { createPanel, createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { AddSystemPanel } from '@bdeploy-pom/panels/systems/add-system.panel';
import { SystemDetailsPanel } from '@bdeploy-pom/panels/systems/system-details.panel';

export class SystemBrowserPage extends BaseDialog {

  constructor(page: Page, private readonly group: string) {
    super(page, 'app-system-browser');
  }

  async goto() {
    await new MainMenu(this.page).getNavButton('Systems').click();
    await this.page.waitForURL(`/#/systems/browser/${this.group}`);
    await this.expectOpen();
  }

  async addSystem() {
    return await createPanel(this.getToolbar(), 'Add System', (p) => new AddSystemPanel(p));
  }

  async getSystemDetailsPanel(name: string) {
    return createPanelFromRow(this.getTableRowContaining(name), p => new SystemDetailsPanel(p));
  }
}
