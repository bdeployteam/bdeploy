import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';

export class LinkExpressionPicker extends BaseArea {
  constructor(parent: Locator) {
    super(parent.page());
  }

  protected getArea(): Locator {
    return this.getOverlayContainer().locator('app-bd-expression-picker');
  }

  async switchTo(tab: string) {
    const tabs = this.getArea().locator('mat-tab-header');
    await tabs.getByRole('tab').getByText(tab).click();
  }
}