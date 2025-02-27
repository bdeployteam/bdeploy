import { BasePanel } from '@bdeploy-pom/base/base-panel';

export class AddGroupPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-add-user-group');
  }

  async fill(groupName: string, description: string) {
    await this.getDialog().getByLabel('User Group Name').fill(groupName);
    await this.getDialog().getByLabel('Description').fill(description);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }
}