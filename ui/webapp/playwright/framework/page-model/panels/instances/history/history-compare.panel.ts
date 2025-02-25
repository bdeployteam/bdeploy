import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class HistoryComparePanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-history-compare');
  }

  getProcessCompare(name: string) {
    return this.getDialog().locator('app-history-process-config', { has: this.page.locator('app-history-diff-field', { hasText: name }) });
  }
}