import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { InstanceSettingsPanel } from '@bdeploy-pom/panels/instances/instance-settings.panel';

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

  async getSettingsPanel() {
    const panel = new InstanceSettingsPanel(this.page);
    // check for the toolbar - the dialog is always "invisible", as it has a zero size
    if(await panel.getToolbar().isVisible()) {
      return Promise.resolve(panel);
    }

    return createPanel(this.getToolbar(), 'Instance Settings', (p) => panel);
  }

  /** get the node configuration container. client application node uses __ClientApplications as name */
  getConfigNode(name: string) {
    return this.getDialog().getByTestId(name);
  }

}