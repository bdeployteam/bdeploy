import { BasePanel } from '@bdeploy-pom/base/base-panel';

export class FileEditorPanel extends BasePanel {
  constructor(page: any) {
    super(page, 'app-editor');
  }

  async fill(content: string) {
    await this.getDialog().locator('.view-line').click();
    await this.getDialog().getByRole('textbox', { name: 'Editor content' }).fill(content);
  }
}