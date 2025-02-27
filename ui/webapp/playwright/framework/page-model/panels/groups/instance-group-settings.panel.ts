import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { InstanceGroupAttributeValuesPanel } from '@bdeploy-pom/panels/groups/instance-group-attribute-values.panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { InstanceGroupPermissionsPanel } from '@bdeploy-pom/panels/groups/instance-group-permissions.panel';

export class InstanceGroupSettingsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-settings');
  }

  async getAttributeValuesPanel() {
    return createPanel(this.getDialog(), 'Group Attribute Values', p => new InstanceGroupAttributeValuesPanel(p));
  }

  async getPermissionsPanel() {
    return createPanel(this.getDialog(), 'Instance Group Permissions', p => new InstanceGroupPermissionsPanel(p));
  }
}