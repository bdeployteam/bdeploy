import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { Locator, Page } from '@playwright/test';
import { NodeAdminPage } from '@bdeploy-pom/primary/admin/node-admin.page';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { expect } from '@bdeploy-setup';
import { GlobalAttributesTab } from '@bdeploy-pom/primary/admin/tabs/global-attributes.tab';
import { UserAccountsPage } from '@bdeploy-pom/primary/admin/user-accounts.page';

export class AdminPage extends BaseDialog {
  readonly _adminMenu: Locator;

  constructor(page: Page) {
    super(page, 'app-admin-shell');

    this._adminMenu = this.page.locator('mat-nav-list');
  }

  async goto() {
    await new MainMenu(this.page).getNavButton('Administration').click();
    await this.page.waitForURL('/#/admin/**');
    await this.expectOpen();
  }

  async gotoNodesPage() {
    await this._adminMenu.locator('a', {hasText: 'Nodes'}).click();
    const nodesPage = new NodeAdminPage(this.page);
    await nodesPage.expectOpen();
    return Promise.resolve(nodesPage);
  }

  async gotoUserAccountsPage() {
    await this._adminMenu.locator('a', { hasText: 'User Accounts' }).click();
    const accountsPage = new UserAccountsPage(this.page);
    await accountsPage.expectOpen();
    return Promise.resolve(accountsPage);
  }

  async gotoGlobalAttributesTab() {
    await this._adminMenu.locator('a', { hasText: 'Settings' }).click();
    const generalDialog = new BaseDialog(this.page, 'app-settings-general');
    await generalDialog.expectOpen();

    const generalTabs = generalDialog.getDialog().locator('mat-tab-header');
    await generalTabs.getByRole('tab').getByText('Global Attributes').click();
    const attributesTab = generalDialog.getDialog().locator('app-attributes-tab');

    await expect(attributesTab).toBeVisible();
    return Promise.resolve(new GlobalAttributesTab(attributesTab, generalDialog.getToolbar()));
  }
}
