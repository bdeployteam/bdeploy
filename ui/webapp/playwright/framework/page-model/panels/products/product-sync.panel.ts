import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { ProductSyncServerPanel } from '@bdeploy-pom/panels/products/product-sync-server.panel';

export class ProductSyncPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-product-sync');
  }

  async uploadProduct() {
    return createPanel(this.getDialog(), 'Upload to managed server', p => new ProductSyncServerPanel(p));
  }

  async downloadProduct() {
    return createPanel(this.getDialog(), 'Download to central server', p => new ProductSyncServerPanel(p));
  }
}