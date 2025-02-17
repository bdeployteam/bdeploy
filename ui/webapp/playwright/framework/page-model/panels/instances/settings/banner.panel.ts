import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { expect } from '@bdeploy-setup';

export class BannerPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-banner');
  }

  async fill(banner: string, color?: string) {
    await this.getDialog().getByLabel('Banner Text').fill(banner);

    if (color) {
      const panel = this.getDialog().locator('mat-expansion-panel', { has: this.page.locator('mat-expansion-panel-header', { hasText: 'Color Scheme' }) });
      await panel.locator('mat-expansion-panel-header').click();

      await panel.locator('app-color-select', { hasText: color }).click();
      await expect(panel.locator('app-color-select', { hasText: color }).locator('mat-icon', { hasText: /.*check.*/ })).toBeVisible();

      await panel.locator('mat-expansion-panel-header').click();
    }
  }

  async apply() {
    await this.getDialog().getByRole('button', { name: 'Apply' }).click();
  }
}