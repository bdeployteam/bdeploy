import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { createPanel, createPanelFromRow } from '@bdeploy-pom/common/common-functions';
import { ReposBrowserPage } from '@bdeploy-pom/primary/repos/repos-browser.page';
import { SoftwareUploadPanel } from '@bdeploy-pom/panels/repos/software-upload.panel';
import { SoftwareDetailsPanel } from '@bdeploy-pom/panels/repos/software-details.panel';
import { RepoSettingsPanel } from '@bdeploy-pom/panels/repos/repo-settings.panel';

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

  async getUploadPanel() {
    return createPanel(this.getToolbar(), 'Upload Software', (p) => new SoftwareUploadPanel(p));
  }

  getSoftwareRow(name: string) {
    return this.getDialog().locator('tbody').getByRole('row', { name: name }).first();
  }

  async getSoftwareDetailsPanel(software: string) {
    return createPanelFromRow(this.getSoftwareRow(software), p => new SoftwareDetailsPanel(p));
  }

  async getRepoSettingsPanel() {
    return createPanel(this.getToolbar(), 'Settings', p => new RepoSettingsPanel(p));
  }
}