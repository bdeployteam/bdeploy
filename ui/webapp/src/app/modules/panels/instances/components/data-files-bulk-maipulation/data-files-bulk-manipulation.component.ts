import { Component, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, finalize } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { DataFilesBulkService } from 'src/app/modules/primary/instances/services/data-files-bulk.service';
import { DataFilePath, DataFilesService } from 'src/app/modules/primary/instances/services/data-files.service';

@Component({
  selector: 'app-data-files-bulk-manipulation',
  templateUrl: './data-files-bulk-manipulation.component.html',
})
export class DataFilesBulkManipulationComponent {
  private areas = inject(NavAreasService);
  private dataFilesService = inject(DataFilesService);
  protected dataFilesBulkService = inject(DataFilesBulkService);

  protected deleting$ = new BehaviorSubject<boolean>(false);

  protected selections: DataFilePath[];

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  protected onDelete() {
    const count = this.dataFilesBulkService.selectedDataFiles.length;
    this.dialog
      .confirm(
        `Delete ${count} data files?`,
        `This will delete <strong>${count}</strong> data files. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null,
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        const minion = this.dataFilesBulkService.selectedDataFiles[0].directory.minion;
        this.deleting$.next(true);
        this.dataFilesBulkService
          .deleteFiles(minion, this.dataFilesBulkService.selectedDataFiles)
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe(() => {
            this.dataFilesService.load();
            this.areas.closePanel();
          });
      });
  }

  protected onDownload() {
    this.dataFilesBulkService.downloadDataFiles(this.dataFilesBulkService.selectedDataFiles);
  }
}
