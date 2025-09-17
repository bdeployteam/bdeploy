import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class MultiNodeAddPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-add-multi-node');
  }

  async fill(name: string, os: string) {
    await this.getDialog().getByLabel('Name').fill(name);
    await new FormSelectElement(this.getDialog(), 'Operating System').selectOption(os, true);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }

}