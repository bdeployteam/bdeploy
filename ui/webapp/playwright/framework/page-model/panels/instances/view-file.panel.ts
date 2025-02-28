import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { Page } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import { DataFileEditorPanel } from '@bdeploy-pom/panels/instances/data-files/data-file-editor.panel';

export class ViewFilePanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-file-viewer');
  }

  async getFileEditorPanel() {
    return createPanel(this.getToolbar(), 'Edit File', p => new DataFileEditorPanel(p));
  }
}