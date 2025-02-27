import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import * as path from 'node:path';
import { UploadDetailsArea } from '@bdeploy-pom/panels/repos/upload-details.area';

export class SoftwareUploadPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-software-upload');
  }

  async upload(fixtureName: string) {
    const chooserEvent = this.page.waitForEvent('filechooser');
    await this.getDialog().getByRole('button', { name: 'browse.' }).click();

    const chooser = await chooserEvent;
    await chooser.setFiles(path.join('playwright', 'fixtures', fixtureName));
  }

  getUploadState(name: string) {
    return this.getDialog().locator('app-bd-file-upload-raw', { hasText: name });
  }

  getUploadDetailsArea(name: string) {
    return new UploadDetailsArea(this.page, this.getUploadState(name));
  }
}