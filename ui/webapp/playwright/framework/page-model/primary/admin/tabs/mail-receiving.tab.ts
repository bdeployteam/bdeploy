import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { EditMailReceivingPanel } from '@bdeploy-pom/panels/admin/edit-mail-receiving.panel';

export class MailReceivingTab extends BaseArea {
  constructor(private readonly tab: Locator, private readonly toolbar: Locator) {
    super(tab.page());
  }

  protected getArea(): Locator {
    return this.tab;
  }

  async edit() {
    return createPanel(this.toolbar, 'Edit', p => new EditMailReceivingPanel(p));
  }
}