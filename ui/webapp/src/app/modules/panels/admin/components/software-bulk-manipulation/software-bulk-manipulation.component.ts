import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { Actions } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { SoftwareUpdateService, SoftwareVersion } from 'src/app/modules/primary/admin/services/software-update.service';
import { SoftwareVersionBulkService } from '../../services/software-version-bulk.service';


import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-software-bulk-manipulation',
    templateUrl: './software-bulk-manipulation.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdButtonComponent, AsyncPipe]
})
export class SoftwareBulkManipulationComponent implements OnInit, OnDestroy {
  private readonly actions = inject(ActionsService);
  private readonly bulk = inject(SoftwareVersionBulkService);
  private readonly software = inject(SoftwareUpdateService);
  private readonly deleting$ = new BehaviorSubject<boolean>(false);

  protected mappedDelete$ = this.actions.action(
    [Actions.DELETE_UPDATES],
    this.deleting$,
    null,
    null,
    this.bulk.selection$.pipe(map((b) => b.map((x) => x.version))),
  );
  protected selections: SoftwareVersion[];
  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.bulk.selection$.subscribe((selections) => (this.selections = selections));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.selections.length} system versions?`,
        `This will delete <strong>${this.selections.length}</strong> system versions. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null,
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
}
