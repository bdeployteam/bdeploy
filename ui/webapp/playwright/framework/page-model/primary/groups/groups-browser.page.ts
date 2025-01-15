import { Page } from '@playwright/test';
import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { AddInstanceGroupPanel } from '@bdeploy-pom/panels/groups/add-group.panel';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { createPanel } from '@bdeploy-pom/common/common-functions';

export class InstanceGroupsBrowserPage extends BaseDialog {

  constructor(page: Page) {
    super(page, 'app-groups-browser');
  }

  async goto() {
    await new MainMenu(this.page).getNavButton('Instance Groups').click();
    await this.page.waitForURL('/#/groups/browser');
    await this.expectOpen();
  }

  async addInstanceGroup(): Promise<AddInstanceGroupPanel> {
    return createPanel(this.getToolbar(), 'Add Instance Group...', (p) => new AddInstanceGroupPanel(p));
  }
}
