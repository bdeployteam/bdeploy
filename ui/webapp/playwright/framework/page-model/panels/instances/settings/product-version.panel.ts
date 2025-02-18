import { BasePanel } from '@bdeploy-pom/base/base-panel';

export class ProductVersionPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-product-update');
  }

  async setVersion(version: string) {
    await this.getTableRowContaining(version).locator('app-update-action').click();
  }
}