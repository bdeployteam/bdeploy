import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import { ProductUploadPanel } from '@bdeploy-pom/panels/products/product-upload.panel';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';

export class ProductsPage extends BaseDialog {
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
    const upload = this.getToolbar().getByLabel('Upload Product...');
    await upload.click();

    const panel = new ProductUploadPanel(this.page);
    await panel.expectOpen();

    return Promise.resolve(panel);
  }

  getProductRow(name: string) {
    return this.getDialog().locator('tbody').getByRole('row', { name: name }).first();
  }
}