import { Component, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, finalize } from 'rxjs';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { DataFilesBulkService } from 'src/app/modules/primary/instances/services/data-files-bulk.service';
import { DataFilesService } from 'src/app/modules/primary/instances/services/data-files.service';

@Component({
  selector: 'app-data-files-bulk-manipulation',
  templateUrl: './data-files-bulk-manipulation.component.html',
})
export class DataFilesBulkManipulationComponent {
  private areas = inject(NavAreasService);
  private dataFilesService = inject(DataFilesService);
  protected dataFilesBulkService = inject(DataFilesBulkService);

  protected starting$ = new BehaviorSubject<boolean>(false);
  protected stopping$ = new BehaviorSubject<boolean>(false);
  protected deleting$ = new BehaviorSubject<boolean>(false);
  protected installing$ = new BehaviorSubject<boolean>(false);
  protected activating$ = new BehaviorSubject<boolean>(false);
  protected isAllSameProduct: boolean;
  protected selections: {
    directory: RemoteDirectory;
    entry: RemoteDirectoryEntry;
  }[];
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  protected onDelete() {
    const count = this.dataFilesBulkService.selection.length;
    this.dialog
      .confirm(
        `Delete ${count} data files?`,
        `This will delete <strong>${count}</strong> data files. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.deleting$.next(true);
        this.dataFilesBulkService
          .deleteFiles()
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe(() => {
            this.dataFilesService.load();
            this.areas.closePanel();
          });
      });
  }

  protected onDownload() {
    this.dataFilesBulkService.downloadDataFile();
  }
}
