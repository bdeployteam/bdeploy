import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { BHiveBrowserPanel } from '@bdeploy-pom/panels/admin/bhive-browser.panel';

export class BHiveDetailsPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-bhive-details');
  }

  async browseContent() {
    return createPanel(this.getDialog(), 'Browse Content', p => new BHiveBrowserPanel(p));
  }
}