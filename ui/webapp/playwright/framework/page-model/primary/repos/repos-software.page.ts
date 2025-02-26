import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { ReposBrowserPage } from '@bdeploy-pom/primary/repos/repos-browser.page';
import { SoftwareUploadPanel } from '@bdeploy-pom/panels/repos/software-upload.panel';

export class ReposSoftwarePage extends BaseDialog {
  constructor(page: Page, private readonly repo: string) {
    super(page, 'app-repository');
  }

  async goto() {
    const repoBrowser = new ReposBrowserPage(this.page);
    await repoBrowser.goto();

    await repoBrowser.getTableRowContaining(this.repo).click();
    await this.page.waitForURL(`/#/repositories/repository/${this.repo}`);
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Software Packages and Products');
  }

  async openUploadPanel() {
    return createPanel(this.getToolbar(), 'Upload Software', (p) => new SoftwareUploadPanel(p));
  }

  getSoftwareRow(name: string) {
    return this.getDialog().locator('tbody').getByRole('row', { name: name }).first();
  }
}