import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { NodeConvertPanel } from '@bdeploy-pom/panels/admin/node-convert.panel';

export class NodeDetailsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-node-details');
  }

  async convertToNode() {
    await this.getDialog().getByLabel('Convert to Node...').click();
    const nodeConvert = new NodeConvertPanel(this.page);
    await nodeConvert.expectOpen();
    return Promise.resolve(nodeConvert);
  }

}