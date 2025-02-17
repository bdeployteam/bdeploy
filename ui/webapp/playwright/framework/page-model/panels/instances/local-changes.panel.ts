import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { LocalDiffPanel } from '@bdeploy-pom/panels/instances/local-diff.panel';

export class LocalChangesPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-local-changes');
  }

  async getCompareWithBasePanel() {
    return createPanel(this.getDialog(), 'Compare Local with Base', p => new LocalDiffPanel(p));
  }
}