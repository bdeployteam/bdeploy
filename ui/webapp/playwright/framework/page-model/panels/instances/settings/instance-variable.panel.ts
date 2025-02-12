import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { VariableGroupArea } from '@bdeploy-pom/panels/instances/settings/variable-group.area';

export class InstanceVariablePanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-instance-variables');
  }

  async getVariableGroup(name: string) {
    return new VariableGroupArea(this.page, this.getDialog(), name);
  }
}