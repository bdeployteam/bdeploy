import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { ParameterGroupArea } from '@bdeploy-pom/panels/instances/process-settings/parameter-group.area';
import { Page } from '@playwright/test';

export class ConfigureParametersPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-configure-process');
  }

  async fillProcessName(newName: string) {
    await this.getDialog().getByLabel("name").fill(newName);
  }

  async getParameterGroup(name: string) {
    return new ParameterGroupArea(this.page, this.getDialog(), name);
  }

  async showCommandPreview() {
    const panel = this.getDialog().locator('mat-expansion-panel', { has: this.page.locator('mat-expansion-panel-header', { hasText: 'Command Line Preview' }) });
    await panel.locator('mat-expansion-panel-header').click();
    await panel.scrollIntoViewIfNeeded();

    // this is to scroll a *little* further, so that the lower border looks a little better on screenshots
    await this.page.mouse.wheel(0, 10);
  }

  getStartCommandPreview() {
    return this.getDialog().locator("app-history-process-config");
  }

  async apply() {
    await this.getToolbar().getByRole('button', { name: 'Apply' }).click();
  }

  getSavePopup() {
    return this.getDialog().locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: 'Save Changes?' });
  }

  getAllowedConfigDirPaths() {
    return this.getDialog().locator('app-bd-form-input', { hasText: 'Allowed Configuration Directory Paths' });
  }
}