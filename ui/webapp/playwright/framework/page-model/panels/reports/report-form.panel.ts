import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';
import { FormCheckboxElement } from '@bdeploy-elements/form-checkbox.element';
import { ReportResultPage } from '@bdeploy-pom/primary/reports/report-result.page';

export class ReportFormPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-report-form');
  }

  async fillLiteral(name: string, value: string) {
    await this.getDialog().getByLabel(name).fill(value);
  }

  async fillSelect(name: string, value: string) {
    await new FormSelectElement(this.getDialog(), name).selectOption(value);
  }

  async fillBoolean(name: string, value: boolean) {
    await new FormCheckboxElement(this.getDialog(), name).setChecked(value);
  }

  async generate() {
    await this.getDialog().getByRole('button', { name: 'Generate' }).click();

    const page = new ReportResultPage(this.page);
    await page.expectOpen();

    return Promise.resolve(page);
  }
}