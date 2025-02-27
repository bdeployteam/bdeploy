import { BasePermissionPanel } from '@bdeploy-pom/panels/base-permission.panel';
import { Page } from '@playwright/test';

export class RepoPermissionsPanel extends BasePermissionPanel {
  constructor(page: Page) {
    super(page, 'app-software-repository-permissions');
  }
}