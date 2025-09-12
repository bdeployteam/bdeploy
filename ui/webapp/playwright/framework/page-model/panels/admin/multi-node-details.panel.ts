import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { expect } from '@bdeploy-setup';

export class MultiNodeDetailsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-multi-node-details');
  }

  async remove(name: string) {
    await this.getDialog().getByLabel('Remove').click();
    const dlg = await this.getLocalMessageDialog('Remove ' + name + '?');
    await expect(dlg).toBeVisible();
    await dlg.getByRole('button', { name: 'Yes' }).click();
    await this.expectClosed();
  }

  async verifyDetails(title: string) {
    const detailsCard = this.getDialog().locator('app-bd-notification-card');

    await detailsCard.getByText(title).isVisible();
    const nodeSpecs = detailsCard.getByTestId('multi-node-specs').locator('div');

    await expect(nodeSpecs.nth(0)).toContainText('Master:');
    await expect(nodeSpecs.nth(1)).toContainText('no');
    await expect(nodeSpecs.nth(2)).toContainText('Node type:');
    await expect(nodeSpecs.nth(3)).toContainText('MULTI');
  }

}