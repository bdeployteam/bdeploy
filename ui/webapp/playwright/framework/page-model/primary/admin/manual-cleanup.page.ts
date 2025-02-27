import { BaseDialog } from '@bdeploy-pom/base/base-dialog';

export class ManualCleanupPage extends BaseDialog {
  constructor(page: any) {
    super(page, 'app-master-cleanup');
  }

  async calculate() {
    await this.getToolbar().getByRole('button', { name: 'Calculate' }).click();
  }
}