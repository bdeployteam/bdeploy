import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class AddLdapServerPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-add-ldap-server');
  }

  async fill(uri: string, description: string, username: string, password: string, base: string) {
    await this.getDialog().getByLabel('Server URL').fill(uri);
    await this.getDialog().getByLabel('Description').first().fill(description); // avoid "Group Description Field" (.first())
    await this.getDialog().getByLabel('User').first().fill(username); // avoid "Account User Field" (.first())
    await this.getDialog().getByLabel('Password').fill(password);
    await this.getDialog().getByLabel('Account and Group Base').fill(base);
  }

  async apply() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }
}