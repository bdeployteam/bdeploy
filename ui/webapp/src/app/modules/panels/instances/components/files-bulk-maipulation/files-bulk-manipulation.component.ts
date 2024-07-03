import { Component, OnDestroy, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription, finalize } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { FilesBulkService } from 'src/app/modules/primary/instances/services/files-bulk.service';
import { FilePath, FilesService } from 'src/app/modules/primary/instances/services/files.service';

@Component({
  selector: 'app-files-bulk-manipulation',
  templateUrl: './files-bulk-manipulation.component.html',
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
