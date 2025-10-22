import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { ConfigureParametersPanel } from '@bdeploy-pom/panels/instances/process-settings/configure-parameters.panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { ConfigureEndpointsPanel } from '@bdeploy-pom/panels/instances/process-settings/configure-endpoints.panel';

export class ProcessSettingsPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-edit-process-overview');
  }

  async getConfigureParametersPanel() {
    return createPanel(this.getDialog(), 'Configure Parameters...', p => new ConfigureParametersPanel(p));
  }

  async getConfigureEndpointsPanel() {
    return createPanel(this.getDialog(), 'Configure Endpoints...', p => new ConfigureEndpointsPanel(p));
  }

  async deleteProcess() {
      await this.getDialog().getByRole('button', { name: 'Delete' }).click();
  }

}