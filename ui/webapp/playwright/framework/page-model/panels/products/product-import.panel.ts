import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class ProductImportPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-product-transfer-repo');
  }

  async selectRepo(repo: string) {
    await new FormSelectElement(this.getDialog(), 'Repository').selectOption(repo);
  }

  async selectProduct(product: string) {
    await new FormSelectElement(this.getDialog(), 'Product').selectOption(product);
  }

  async selectVersion(version: string) {
    await this.getTableRowContaining(version).getByRole('checkbox').click();
  }

  async transfer() {
    await this.getDialog().getByRole('button', { name: 'Import' }).click();
  }
}