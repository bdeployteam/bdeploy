import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export abstract class BasePermissionPanel extends BasePanel {
  protected constructor(page: Page, selector: string) {
    super(page, selector);
  }
}