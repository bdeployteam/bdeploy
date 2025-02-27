import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { AddLdapServerPanel } from '@bdeploy-pom/panels/admin/add-ldap-server.panel';

export class LDAPServersTab extends BaseArea {
  constructor(private readonly tab: Locator, private readonly toolbar: Locator) {
    super(tab.page());
  }

  protected getArea(): Locator {
    return this.tab;
  }

  async addServer() {
    return createPanel(this.toolbar, 'New Server...', p => new AddLdapServerPanel(p));
  }
}