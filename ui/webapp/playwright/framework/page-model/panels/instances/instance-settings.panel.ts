import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { InstanceTemplatesPanel } from '@bdeploy-pom/panels/instances/settings/instance-templates.panel';
import { ManageNodesPanel } from '@bdeploy-pom/panels/instances/settings/manage-nodes.panel';
import { InstanceVariablePanel } from '@bdeploy-pom/panels/instances/settings/instance-variable.panel';
import { ConfigFilesPanel } from '@bdeploy-pom/panels/instances/settings/config-files.panel';
import { ProductVersionPanel } from '@bdeploy-pom/panels/instances/settings/product-version.panel';
import { BannerPanel } from '@bdeploy-pom/panels/instances/settings/banner.panel';
import { BaseConfigurationPanel } from '@bdeploy-pom/panels/instances/settings/base-configuration.panel';

export class InstanceSettingsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-instance-settings');
  }

  async getBaseConfigurationPanel() {
    return createPanel(this.getDialog(), 'Base Configuration', p => new BaseConfigurationPanel(p));
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

  async getConfigurationFilesPanel() {
    return createPanel(this.getDialog(), 'Configuration Files', p => new ConfigFilesPanel(p));
  }

  async getProductVersionPanel() {
    return createPanel(this.getDialog(), 'Update Product Version', p => new ProductVersionPanel(p));
  }

  async getBannerPanel() {
    return createPanel(this.getDialog(), 'Banner...', p => new BannerPanel(p));
  }

  async delete() {
    await this.getDialog().getByRole('button', { name: 'Delete Instance' }).click();
    const dlg = await this.getLocalMessageDialog('Delete');
    await dlg.getByRole('button', { name: 'Yes' }).click();
    await this.page.waitForURL('/#/instances/browser/**');
  }
}