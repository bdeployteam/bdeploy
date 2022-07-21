import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import {
  SoftwareUpdateService,
  SoftwareVersion,
} from 'src/app/modules/primary/admin/services/software-update.service';
import { SoftwareVersionBulkService } from '../../services/software-version-bulk.service';

@Component({
  selector: 'app-software-bulk-manipulation',
  templateUrl: './software-bulk-manipulation.component.html',
})
export class SoftwareBulkManipulationComponent implements OnInit, OnDestroy {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ selections: SoftwareVersion[];
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(
    private bulk: SoftwareVersionBulkService,
    private software: SoftwareUpdateService
  ) {}

  ngOnInit(): void {
    this.subscription = this.bulk.selection$.subscribe(
      (selections) => (this.selections = selections)
    );
  }

  /* template */ onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.selections.length} system versions?`,
        `This will delete <strong>${this.selections.length}</strong> system versions. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.deleting$.next(true);
        this.bulk
          .delete()
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe(() => {
            this.tb.closePanel();
            this.software.load();
          });
      });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
