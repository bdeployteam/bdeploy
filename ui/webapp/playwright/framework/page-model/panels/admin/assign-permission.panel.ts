import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class AssignPermissionPanel extends BasePanel {
  constructor(page: any, group = false) {
    super(page, group ? 'app-assign-user-group-permission' : 'app-user-assign-permission');
  }

  async fill(scope: string, permission: string) {
    await new FormSelectElement(this.getDialog(), 'Select Permission Scope').selectOption(scope);
    await new FormSelectElement(this.getDialog(), 'Permission').selectOption(permission);
  }

  async save() {
    await this.getDialog().getByRole('button', { name: 'Save' }).click();
  }
}