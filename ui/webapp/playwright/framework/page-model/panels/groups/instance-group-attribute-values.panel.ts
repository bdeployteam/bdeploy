import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Locator, Page } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';
import { expect } from '@bdeploy-setup';

export class InstanceGroupAttributeValuesPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-attribute-values');
  }

  async addAttributeValue() {
    await this.getDialog().getByLabel('Set Attribute Value...').click();
    const dlg = await this.getLocalMessageDialog('Set Attribute Value');

    return Promise.resolve(dlg);
  }

  async fillAttributeValueDialog(dlg: Locator, attribute: string, value: string) {
    await new FormSelectElement(dlg, 'Group Attribute').selectOption(attribute);
    await dlg.getByLabel('Attribute Value').fill(value);

    await expect(dlg.getByRole('button', { name: 'Apply' })).toBeEnabled();
  }

  async applyAttributeValueDialog(dlg: Locator) {
    await dlg.getByRole('button', { name: 'Apply' }).click();
    await expect(dlg).not.toBeAttached();
  }
}