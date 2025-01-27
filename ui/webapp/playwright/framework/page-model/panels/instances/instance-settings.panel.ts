import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { InstanceTemplatesPanel } from '@bdeploy-pom/panels/instances/settings/instance-templates.panel';

export class InstanceSettingsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-instance-settings');
  }

  async getInstanceTemplatesPanel() {
    return createPanel(this.getDialog(), 'Instance Templates', p => new InstanceTemplatesPanel(p));
  }
}