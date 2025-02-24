import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { HistoryComparePanel } from '@bdeploy-pom/panels/instances/history/history-compare.panel';

export class HistoryEntryDetailsPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-history-entry');
  }

  async getCompareWithCurrentPanel() {
    return createPanel(this.getDialog(), 'Compare with Current', p => new HistoryComparePanel(p));
  }
}