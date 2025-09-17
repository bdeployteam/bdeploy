import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Page } from '@playwright/test';
import { NodeDetailsPanel } from '@bdeploy-pom/panels/admin/node-details.panel';
import { NodeAddPanel } from '@bdeploy-pom/panels/admin/node-add.panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { MultiNodeAddPanel } from '@bdeploy-pom/panels/admin/multi-node-add.panel';
import { MultiNodeDetailsPanel } from '@bdeploy-pom/panels/admin/multi-node-details.panel';

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

  async selectMultiNode(name: string) {
    await this.getTableRowContaining(name).click();
    const multiNodeDetailsPanel = new MultiNodeDetailsPanel(this.page);
    await multiNodeDetailsPanel.expectOpen();
    return Promise.resolve(multiNodeDetailsPanel);
  }

  async addNode() {
    return createPanel(this.getToolbar(), 'Add Node...', (p) => new NodeAddPanel(p));
  }

  async addMultiNode() {
    return createPanel(this.getToolbar(), 'Add Multi-Node...', (p) => new MultiNodeAddPanel(p));
  }

  shouldHaveTableRows(nrOfExpectedRows: number) {
    return expect(this.getArea().locator('tbody').getByRole('row')).toHaveCount(nrOfExpectedRows);
  }
}