import { BasePanel } from '@bdeploy-pom/base/base-panel';

export class AddRepoPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-add-repository');
  }

  async fill(name: string, description: string) {
    await this.getDialog().getByLabel('Name').fill(name);
    await this.getDialog().getByLabel('Description').fill(description);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }
}