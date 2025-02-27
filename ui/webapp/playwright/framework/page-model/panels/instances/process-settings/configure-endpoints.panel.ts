import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class ConfigureEndpointsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-configure-endpoints');
  }

  async apply() {
    await this.getToolbar().getByRole('button', { name: 'Apply' }).click();
  }
}