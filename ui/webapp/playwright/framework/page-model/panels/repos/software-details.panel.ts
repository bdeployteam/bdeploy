import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class SoftwareDetailsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-software-details');
  }
}