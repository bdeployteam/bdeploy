import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';

export class DataFileEditorPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-data-file-editor');
  }

  async fill(content: string) {
    await this.getDialog().locator('.view-line').click();
    await this.getDialog().getByRole('textbox', { name: 'Editor content' }).fill(content);
  }
}