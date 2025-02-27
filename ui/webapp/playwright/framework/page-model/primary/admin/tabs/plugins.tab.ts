import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';

export class PluginsTab extends BaseArea {
  constructor(private readonly tab: Locator, private readonly toolbar: Locator) {
    super(tab.page());
  }

  protected getArea(): Locator {
    return this.tab;
  }
}