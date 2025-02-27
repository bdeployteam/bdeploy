import { BasePermissionPanel } from '@bdeploy-pom/panels/base-permission.panel';
import { Page } from '@playwright/test';

export class InstanceGroupPermissionsPanel extends BasePermissionPanel {
  constructor(page: Page) {
    super(page, 'app-instance-group-permissions');
  }
}