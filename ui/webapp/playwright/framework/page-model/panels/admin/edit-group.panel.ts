import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class EditGroupPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-edit-user-group');
  }
}