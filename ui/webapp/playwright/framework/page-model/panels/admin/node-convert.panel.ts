import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class NodeConvertPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-node-conversion');
  }
}