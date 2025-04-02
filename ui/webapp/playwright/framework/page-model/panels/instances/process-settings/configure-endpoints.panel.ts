import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { EndpointArea } from '@bdeploy-pom/panels/instances/process-settings/endpoint.area';

export class ConfigureEndpointsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-configure-endpoints');
  }

  async apply() {
    await this.getToolbar().getByRole('button', { name: 'Apply' }).click();
  }

  async getEndpointArea(id: string) {
    return new EndpointArea(this.page, this.getDialog(), id);
  }
}