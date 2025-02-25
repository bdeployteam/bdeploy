import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import * as path from 'node:path';

export class AddDataFilePanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-add-data-file');
  }

  async fill(name: string, fixture?: string) {
    if (fixture) {
      const chooserEvent = this.page.waitForEvent('filechooser');
      await this.getDialog().getByRole('button', { name: 'browse.' }).click();
      const chooser = await chooserEvent;

      await chooser.setFiles(path.join('playwright', 'fixtures', fixture));
    }
    // do this second, as browsing for the fixture will automatically apply its name.
    await this.getDialog().getByLabel('File Name').fill(name);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }
}