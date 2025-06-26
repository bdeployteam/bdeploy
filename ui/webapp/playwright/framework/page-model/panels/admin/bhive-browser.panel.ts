import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class BHiveBrowserPanel extends BasePanel {

  constructor(page: Page) {
    super(page, 'app-bd-bhive-browser');
  }

}