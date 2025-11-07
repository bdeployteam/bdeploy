import { BasePanel } from '@bdeploy-pom/base/base-panel';

export class MultiNodeProcessStatusPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-multi-node-process-status');
  }

  getGoToConfig() {
    return this.getDialog().getByTestId('go-to-config-process');
  }

  getStatusReport() {
    return this.getDialog().getByTestId('multi-process-status-report');
  }

  getPortStates() {
    return this.getDialog().getByTestId('port-states');
  }

  getActualityStatus() {
    return this.getDialog().getByTestId('actuality-status');
  }
}