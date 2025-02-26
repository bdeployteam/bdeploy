import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { ProductSyncVersionPanel } from '@bdeploy-pom/panels/products/product-sync-version.panel';

export class ProductSyncServerPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-select-managed-server');
  }

  async selectServer(server: string) {
    return createPanelFromRow(this.getTableRowContaining(server), p => new ProductSyncVersionPanel(p));
  }
}