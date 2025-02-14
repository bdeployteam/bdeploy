import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { Page } from '@playwright/test';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import * as path from 'node:path';
import { expect } from '@bdeploy-setup';

export class SystemTemplatePage extends BaseDialog {
  constructor(page: Page, private readonly group: string) {
    super(page, 'app-system-template');
  }

  async goto() {
    const instances = new InstancesBrowserPage(this.page, this.group);
    await instances.goto();
    await instances.getToolbar().getByLabel('Apply System Template...').click();
    await this.expectOpen();
    await expect(this.getTitle()).toContainText('Apply System Template');
  }

  async uploadTemplate(name: string) {
    const chooserEvent = this.page.waitForEvent('filechooser');
    await this.getDialog().getByRole('button', { name: 'browse.' }).click();

    const chooser = await chooserEvent;
    await chooser.setFiles(path.join('playwright', 'fixtures', name));
  }

  async checkTemplateLoaded(name: string) {
    await expect(this.getDialog().locator('app-bd-notification-card', { hasText: name })).toBeVisible();
  }

}