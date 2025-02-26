import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import * as path from 'node:path';

export class LinkManagedServerPanel extends BasePanel{
  constructor(page: Page) {
    super(page, 'app-link-managed');
  }

  async fillManagedInfo(fixtureName: string, description: string, uriOverride: string) {
    const chooserEvent = this.page.waitForEvent('filechooser');
    await this.getDialog().getByRole('button', { name: 'browse.' }).click();
    const chooser = await chooserEvent;

    await chooser.setFiles(path.join('playwright', 'fixtures', fixtureName));

    await this.getDialog().getByLabel('Description').fill(description);
    await this.getDialog().getByLabel('URI').fill(uriOverride);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }
}