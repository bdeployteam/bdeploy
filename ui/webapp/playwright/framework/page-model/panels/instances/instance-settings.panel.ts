import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { InstanceTemplatesPanel } from '@bdeploy-pom/panels/instances/settings/instance-templates.panel';
import { ManageNodesPanel } from '@bdeploy-pom/panels/instances/settings/manage-nodes.panel';
import { InstanceVariablePanel } from '@bdeploy-pom/panels/instances/settings/instance-variable.panel';

export class InstanceSettingsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-instance-settings');
  }

  async getInstanceTemplatesPanel() {
    return createPanel(this.getDialog(), 'Instance Templates', p => new InstanceTemplatesPanel(p));
  }

  async getManageNodesPanel() {
    return createPanel(this.getDialog(), 'Manage Nodes...', p => new ManageNodesPanel(p));
  }

  async getInstanceVariablePanel() {
    return createPanel(this.getDialog(), 'Instance Variables...', p => new InstanceVariablePanel(p));
  }
}