import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { LaunchConfirmationPopup } from '@bdeploy-pom/panels/instances/process-status/launch-confirmation.popup';

export class ProcessStatusPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-process-status');
  }

  async start() {
    await this.getDialog().getByTestId('start-process').click();
  }

  async stop() {
    await this.getDialog().getByTestId('stop-process').click();
  }

  async restart() {
    await this.getDialog().getByTestId('restart-process').click();
  }

  getConfirmationPopup() {
    return new LaunchConfirmationPopup(this.getDialog());
  }
}