import { Page } from '@playwright/test';
import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { expect } from '@bdeploy-setup';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';
import { FormCheckboxElement } from '@bdeploy-elements/form-checkbox.element';

export class AddInstancePanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-add-instance');
  }

  async fill(name: string, description: string, purpose: InstancePurpose,
             productName: string, productVersion: string, versionRegex: string = null, autoStart = false,
             autoUninstall = true, system: string = null) {
    await this.getDialog().getByLabel('Name').fill(name);
    await this.getDialog().getByLabel('Description').fill(description);
    await new FormSelectElement(this.getDialog(), 'Purpose').selectOption(purpose);

    if (system) {
      await new FormSelectElement(this.getDialog(), 'System').selectOption(system);
    }

    await new FormCheckboxElement(this.getDialog(), 'Automatic Startup').setChecked(autoStart);
    await new FormCheckboxElement(this.getDialog(), 'Automatic Uninstall').setChecked(autoUninstall);

    await new FormSelectElement(this.getDialog(), 'Product').selectOption(productName);
    await new FormSelectElement(this.getDialog(), 'Product Version').selectOption(productVersion);

    if (versionRegex) {
      await this.getDialog().getByLabel('Product Version Regular Expression').fill(versionRegex);
    }
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
    await expect(this.getDialog()).not.toBeAttached();
  }
}
