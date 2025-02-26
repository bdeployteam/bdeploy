import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import * as path from 'node:path';

export class LinkInstanceGroupPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-link-central');
  }

  async toggleOfflineLinking() {
    await this.getDialog().locator('mat-expansion-panel-header', { hasText: 'Manual and Offline Linking' }).click();
  }

  async downloadOfflineLinking(filename: string) {
    const downloadPromise = this.page.waitForEvent('download');

    await this.getDialog().getByRole('button', { name: 'Download Link Information' }).click();

    const download = await downloadPromise;
    await download.saveAs(path.join('playwright', 'fixtures', filename));
  }
}