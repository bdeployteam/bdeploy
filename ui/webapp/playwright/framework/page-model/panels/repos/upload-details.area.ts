import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator, Page } from '@playwright/test';
import { FormCheckboxElement } from '@bdeploy-elements/form-checkbox.element';

export class UploadDetailsArea extends BaseArea {
  constructor(page: Page, private readonly parent: Locator) {
    super(page);
  }

  protected getArea(): Locator {
    return this.parent;
  }

  async fill(name: string, version: string, osIndependent: boolean, windows: boolean, linux: boolean) {
    await this.getArea().getByLabel('Name').fill(name);
    await this.getArea().getByLabel('Version').fill(version);

    await new FormCheckboxElement(this.getArea(), 'Operating System Independent').setChecked(osIndependent);
    await new FormCheckboxElement(this.getArea(), 'Windows').setChecked(windows);
    await new FormCheckboxElement(this.getArea(), 'Linux').setChecked(linux);
  }

  async import() {
    await this.getArea().getByRole('button', { name: 'Import' }).click();
  }

  async cancel() {
    await this.getArea().getByRole('button', { name: 'Cancel' }).click();
  }
}