import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { LaunchConfirmationPopup } from '@bdeploy-pom/panels/instances/process-status/launch-confirmation.popup';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { ProcessConsolePanel } from '@bdeploy-pom/panels/instances/process-status/process-console.panel';

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

  async getProcessConsole() {
    return createPanel(this.getDialog(), 'Process Console', p => new ProcessConsolePanel(p));
  }
}