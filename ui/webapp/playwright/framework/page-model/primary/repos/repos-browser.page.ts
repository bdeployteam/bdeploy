import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { AddRepoPanel } from '@bdeploy-pom/panels/repos/add-repo.panel';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';

export class ReposBrowserPage extends BaseDialog {
  constructor(page: Page) {
    super(page, 'app-repositories-browser');
  }

  async goto() {
    await new MainMenu(this.page).getNavButton('Software Repositories').click();
    await this.page.waitForURL('/#/repositories/browser');
    await this.expectOpen();
  }

  async getAddRepoPanel() {
    return createPanel(this.getToolbar(), 'Add Software Repository', p => new AddRepoPanel(p));
  }
}