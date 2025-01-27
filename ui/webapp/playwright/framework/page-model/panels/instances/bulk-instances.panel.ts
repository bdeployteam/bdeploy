import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class BulkInstancesPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-bulk-manipulation');
  }

  async setProductVersion(version: string) {
    await this.getDialog().getByLabel('Set Product Version').click();
    const dlg = await this.getLocalMessageDialog('Choose Target Product Version');

    await new FormSelectElement(dlg, '').selectOption(version);
    await dlg.getByLabel('Apply').click();
  }
}