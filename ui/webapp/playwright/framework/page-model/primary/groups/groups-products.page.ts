import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import { ProductUploadPanel } from '@bdeploy-pom/panels/products/product-upload.panel';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { ProductSyncPanel } from '@bdeploy-pom/panels/products/product-sync.panel';
import { ProductImportPanel } from '@bdeploy-pom/panels/products/product-import.panel';

export class GroupsProductsPage extends BaseDialog {
  constructor(page: Page, private readonly group: string) {
    super(page, 'app-products-browser');
  }

  async goto() {
    const instanceBrowser = new InstancesBrowserPage(this.page, this.group);
    await instanceBrowser.goto();

    const mainMenu = new MainMenu(this.page);
    await mainMenu.getNavButton('Products').click();

    await this.page.waitForURL(`/#/products/browser/${this.group}`);
    await expect(this.getTitle()).toContainText('Products');
  }

  async openUploadPanel() {
    return createPanel(this.getToolbar(), 'Upload Product...', (p) => new ProductUploadPanel(p));
  }

  async openProductSyncPanel() {
    return createPanel(this.getToolbar(), 'Synchronize Product Versions', p => new ProductSyncPanel(p));
  }

  async openProductImportPanel() {
    return createPanel(this.getToolbar(), 'Import Product...', p => new ProductImportPanel(p));
  }

  getProductRow(name: string) {
    return this.getDialog().locator('tbody').getByRole('row', { name: name }).first();
  }
}