import { BasePanel } from '@bdeploy-pom/base/base-panel';

export class AddUserPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-add-user');
  }

  async fill(username: string, fullName: string, email: string, password: string, passwordConfirm: string) {
    await this.getDialog().getByLabel('Username').fill(username);
    await this.getDialog().getByLabel('Full Name').fill(fullName);
    await this.getDialog().getByLabel('E-Mail Address').fill(email);
    await this.getDialog().getByLabel('New Password').fill(password);
    await this.getDialog().getByLabel('Confirm Password').fill(passwordConfirm);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }
}