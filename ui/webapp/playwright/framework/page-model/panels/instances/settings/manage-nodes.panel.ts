import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class ManageNodesPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-nodes');
  }

  async selectNode(nodeName: string) {
    await this.page.locator("mat-checkbox", {hasText: nodeName}).click();
  }

  async apply() {
    await this.page.getByRole('button', { name: 'Apply' }).click();
  }
}