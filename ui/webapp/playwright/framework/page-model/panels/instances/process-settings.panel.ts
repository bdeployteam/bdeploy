import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { ConfigureParametersPanel } from '@bdeploy-pom/panels/instances/process-settings/configure-parameters.panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';

export class ProcessSettingsPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-edit-process-overview');
  }

  async getConfigureParametersPanel() {
    return createPanel(this.getDialog(), 'Configure Parameters...', p => new ConfigureParametersPanel(p));
  }
}