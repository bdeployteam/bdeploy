import { BasePanel } from '@bdeploy-pom/base/base-panel';

export class ProductSyncVersionPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-managed-transfer');
  }

  async selectVersion(version: string) {
    await this.getTableRowContaining(version).getByRole('checkbox').click();
  }

  async transfer() {
    await this.getDialog().getByRole('button', { name: 'Transfer' }).click();
  }
}