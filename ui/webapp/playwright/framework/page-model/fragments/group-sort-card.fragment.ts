import { BaseDialog } from '@bdeploy-pom/base/base-dialog';
import { expect, Locator } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

export class GroupSortCard {
  private readonly _groupByButton: Locator;
  private readonly _groupPanel: Locator;

  constructor(private readonly dialog: BaseDialog) {
    // special fetching of button with dynamic text on it.
    this._groupByButton = dialog.getToolbar().locator('button', { hasText: 'Group By:' });
    this._groupPanel = dialog.getOverlayContainer().locator('mat-card', { hasText: 'Grouping' });
  }

  async openGroupingPanel() {
    await this._groupByButton.click();
    await expect(this._groupPanel).toBeVisible();
  }

  async closeGroupingPanel() {
    await this.dialog.closeOverlayByBackdropClick();
  }

  async selectGrouping(grouping: string, level: string = '1st') {
    const level1 = this._groupPanel.locator('app-bd-data-grouping-panel', { hasText: level });
    await new FormSelectElement(level1, 'Grouping').selectOption(grouping);
  }

}