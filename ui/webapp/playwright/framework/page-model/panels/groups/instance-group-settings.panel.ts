import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { InstanceGroupAttributeValuesPanel } from '@bdeploy-pom/panels/groups/instance-group-attribute-values.panel';

export class InstanceGroupSettingsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-settings');
  }

  async gotoAttributeValues() {
    await this.getDialog().getByLabel('Group Attribute Values').click();
    const panel = new InstanceGroupAttributeValuesPanel(this.page);
    await panel.expectOpen();
    return Promise.resolve(panel);
  }
}