import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { Locator, Page } from '@playwright/test';
import { NodeAdminPage } from '@bdeploy-pom/primary/admin/node-admin.page';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { expect } from '@bdeploy-setup';
import { GlobalAttributesTab } from '@bdeploy-pom/primary/admin/tabs/global-attributes.tab';
import { UserAccountsPage } from '@bdeploy-pom/primary/admin/user-accounts.page';
import { MailSendingTab } from '@bdeploy-pom/primary/admin/tabs/mail-sending.tab';
import { MailReceivingTab } from '@bdeploy-pom/primary/admin/tabs/mail-receiving.tab';

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
    const { generalDialog, mailTab } = await this.gotoTab('Global Attributes', 'app-attributes-tab');
    return Promise.resolve(new GlobalAttributesTab(mailTab, generalDialog.getToolbar()));
  }

  async gotoMailSendingTab() {
    const { generalDialog, mailTab } = await this.gotoTab('Mail Sending', 'app-mail-sending-tab');
    return Promise.resolve(new MailSendingTab(mailTab, generalDialog.getToolbar()));
  }

  async gotoMailReceivingTab() {
    const { generalDialog, mailTab } = await this.gotoTab('Mail Receiving', 'app-mail-receiving-tab');
    return Promise.resolve(new MailReceivingTab(mailTab, generalDialog.getToolbar()));
  }

  private async gotoTab(name: string, selector: string) {
    await this._adminMenu.locator('a', { hasText: 'Settings' }).click();
    const generalDialog = new BaseDialog(this.page, 'app-settings-general');
    await generalDialog.expectOpen();

    const generalTabs = generalDialog.getDialog().locator('mat-tab-header');
    await generalTabs.getByRole('tab').getByText(name).click();
    const attributesTab = generalDialog.getDialog().locator(selector);

    await expect(attributesTab).toBeVisible();
    return { generalDialog, mailTab: attributesTab };
  }
}
