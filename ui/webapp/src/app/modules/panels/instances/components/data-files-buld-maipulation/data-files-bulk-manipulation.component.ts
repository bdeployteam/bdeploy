import { Component, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
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
  /* template */ starting$ = new BehaviorSubject<boolean>(false);
  /* template */ stopping$ = new BehaviorSubject<boolean>(false);
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);
  /* template */ isAllSameProduct: boolean;
  /* template */ selections: {
    directory: RemoteDirectory;
    entry: RemoteDirectoryEntry;
  }[];
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    public dataFilesBulkService: DataFilesBulkService,
    private areas: NavAreasService,
    private dataFilesService: DataFilesService
  ) {}

  /* template */ onDelete() {
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
        this.dataFilesBulkService.deleteFiles().then(() => {
          this.deleting$.next(false);
          this.dataFilesService.load();
          this.areas.closePanel();
        });
      });
  }

  /* template */ onDownload() {
    this.dataFilesBulkService.downloadDataFile();
  }
}
