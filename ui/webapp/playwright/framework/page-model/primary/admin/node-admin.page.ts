import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { Page } from '@playwright/test';
import { NodeDetailsPanel } from '@bdeploy-pom/panels/admin/node-details.panel';
import { NodeAddPanel } from '@bdeploy-pom/panels/admin/node-add.panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';

export class NodeAdminPage extends BaseDialog {
  constructor(page: Page) {
    super(page, 'app-nodes');
  }

  async selectNode(name: string) {
    await this.getTableRowContaining(name).click();
    const nodeDetails = new NodeDetailsPanel(this.page);
    await nodeDetails.expectOpen();
    return Promise.resolve(nodeDetails);
  }

  async addNode() {
    return createPanel(this.getToolbar(), 'Add Node...', (p) => new NodeAddPanel(p));
  }
}