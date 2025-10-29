import { Component, inject } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { UploadStatus } from 'src/app/modules/core/services/upload.service';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFileDropComponent } from '../../../../core/components/bd-file-drop/bd-file-drop.component';
import { BdFileUploadComponent } from '../../../../core/components/bd-file-upload/bd-file-upload.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-add-plugin',
    templateUrl: './add-plugin.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdFileDropComponent, BdFileUploadComponent, AsyncPipe]
})
export class AddPluginComponent {
  protected readonly plugins = inject(PluginAdminService);

  protected files: File[] = [];

  protected resultEvaluator(result: UploadStatus<PluginInfoDto>): string {
    if (!result.detail) {
      return null;
    }

    const details = result.detail;
    return `Added ${details.name} ${details.version}`;
  }

  protected fileAdded(file: File) {
    this.files.push(file);
  }

  protected onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
