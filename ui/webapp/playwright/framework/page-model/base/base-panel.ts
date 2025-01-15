import { Page } from '@playwright/test';
import { BaseDialog } from '@bdeploy-pom/base/base-dialog';

export class BasePanel extends BaseDialog {
  constructor(page: Page, selector: string) {
    super(page, selector, 'app-main-nav-flyin');
  }

  getCloseButton() {
    return this.getToolbar().getByLabel('Close');
  }
}
