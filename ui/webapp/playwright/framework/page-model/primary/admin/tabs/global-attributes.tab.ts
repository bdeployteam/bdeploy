import { Locator } from '@playwright/test';
import { GlobalAttributeAddPanel } from '@bdeploy-pom/panels/admin/global-attribute-add.panel';
import { BaseArea } from '@bdeploy-pom/base/base-area';
import { expect } from '@bdeploy-setup';
import { createPanel } from '@bdeploy-pom/common/common-functions';

export class GlobalAttributesTab extends BaseArea {
  constructor(private readonly tab: Locator, private readonly toolbar: Locator) {
    super(tab.page());
  }

  protected override getArea(): Locator {
    return this.tab;
  }

  async addAttribute() {
    return createPanel(this.toolbar, 'New Attribute...', p => new GlobalAttributeAddPanel(p));
  }

  async save() {
    const saveButton = this.toolbar.getByLabel('Save');
    await expect(saveButton).toBeEnabled();
    await saveButton.click();
    await expect(saveButton).toBeDisabled();
  }
}