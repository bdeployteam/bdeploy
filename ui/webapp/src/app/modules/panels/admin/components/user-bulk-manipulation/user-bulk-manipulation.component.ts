import { Component, inject, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject, finalize, map, switchMap } from 'rxjs';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';
import { ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { UserBulkService } from '../../services/user-bulk.service';
import {
  BdBulkOperationResultComponent
} from '../../../../core/components/bd-bulk-operation-result/bd-bulk-operation-result.component';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatDivider } from '@angular/material/divider';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-user-bulk-manipulation',
    templateUrl: './user-bulk-manipulation.component.html',
  imports: [BdBulkOperationResultComponent, BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, MatDivider, BdPanelButtonComponent, BdButtonComponent, AsyncPipe]
})
export class UserBulkManipulationComponent {
  private readonly auth = inject(AuthenticationService);
  protected readonly actions = inject(ActionsService);
  protected readonly bulk = inject(UserBulkService);

  protected loading$ = new BehaviorSubject<boolean>(false);

  protected currentUserSelected$ = this.bulk.selection$.pipe(
    map((users) => users.some((user) => user.name === this.auth.getCurrentUsername())),
  );

  protected bulkOpResult: BulkOperationResultDto;
  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild('opResult') private readonly opResult: TemplateRef<unknown>;

  protected onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} users?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> users. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null,
      )
      .subscribe((r) => {
        if (r) {
          this.delete();
        }
      });
  }

  private delete() {
    this.loading$.next(true);
    this.bulk
      .delete()
      .pipe(
        switchMap((r) => {
          this.bulkOpResult = r;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.loading$.next(false)),
      )
      .subscribe();
  }

  protected onSetInactive(inactive: boolean) {
    this.loading$.next(true);
    this.bulk
      .setInactive(inactive)
      .pipe(
        switchMap((r) => {
          this.bulkOpResult = r;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.loading$.next(false)),
      )
      .subscribe();
  }
}
