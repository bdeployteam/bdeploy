import { Component, TemplateRef, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, finalize, switchMap } from 'rxjs';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';
import { ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { UserGroupBulkService } from '../../services/user-group-bulk.service';

@Component({
  selector: 'app-user-group-bulk-manipulation',
  templateUrl: './user-group-bulk-manipulation.component.html',
})
export class UserGroupBulkManipulationComponent {
  protected actions = inject(ActionsService);
  protected bulk = inject(UserGroupBulkService);

  protected loading$ = new BehaviorSubject<boolean>(false);

  protected bulkOpResult: BulkOperationResultDto;
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('opResult') private opResult: TemplateRef<any>;

  protected onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} user groups?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> user groups. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null
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
        finalize(() => this.loading$.next(false))
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
        finalize(() => this.loading$.next(false))
      )
      .subscribe();
  }
}
