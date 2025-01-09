import { Component, inject } from '@angular/core';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFileDropComponent } from '../../../../core/components/bd-file-drop/bd-file-drop.component';
import { BdFileUploadRawComponent } from '../../../../core/components/bd-file-upload-raw/bd-file-upload-raw.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-software-upload',
    templateUrl: './software-upload.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdFileDropComponent, BdFileUploadRawComponent, AsyncPipe]
})
export class SoftwareUploadComponent {
  protected readonly repositoryService = inject(RepositoryService);

  protected files: File[] = [];

  protected fileAdded(file: File) {
    this.files.push(file);
  }

  protected onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
