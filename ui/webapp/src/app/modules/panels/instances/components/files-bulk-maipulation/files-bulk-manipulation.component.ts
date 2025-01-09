import { Component, inject, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, finalize, Subscription } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { FilesBulkService } from 'src/app/modules/primary/instances/services/files-bulk.service';
import { FilePath, FilesService } from 'src/app/modules/primary/instances/services/files.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-files-bulk-manipulation',
    templateUrl: './files-bulk-manipulation.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, MatDivider, BdButtonComponent, AsyncPipe]
})
export class FilesBulkManipulationComponent implements OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly filesService = inject(FilesService);
  protected readonly filesBulkService = inject(FilesBulkService);

  private readonly subscription: Subscription;
  protected deleting$ = new BehaviorSubject<boolean>(false);
  protected selections: FilePath[];
  protected showDelete = false;

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  constructor() {
    this.subscription = this.areas.primaryRoute$.subscribe((s) => (this.showDelete = s.data['isDataFiles']));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onDelete() {
    const count = this.filesBulkService.selectedFiles.length;
    this.dialog
      .confirm(
        `Delete ${count} files?`,
        `This will delete <strong>${count}</strong> files. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null,
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        const minion = this.filesBulkService.selectedFiles[0].directory.minion;
        this.deleting$.next(true);
        this.filesBulkService
          .deleteFiles(minion, this.filesBulkService.selectedFiles)
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe(() => {
            this.filesService.loadDataFiles();
            this.areas.closePanel();
          });
      });
  }
}
