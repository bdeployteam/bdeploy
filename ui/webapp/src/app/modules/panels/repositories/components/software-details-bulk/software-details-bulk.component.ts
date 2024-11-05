import { Component, inject, ViewChild } from '@angular/core';
import { BehaviorSubject, finalize, map, switchMap } from 'rxjs';
import { Actions } from 'src/app/models/gen.dtos';
import { BdBulkOperationResultConfirmationPromptComponent } from 'src/app/modules/core/components/bd-bulk-operation-result-confirmation-prompt/bd-bulk-operation-result-confirmation-prompt.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { ConfirmationService } from 'src/app/modules/core/services/confirmation.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { SoftwareDetailsBulkService } from '../../services/software-details-bulk.service';

@Component({
  selector: 'app-software-details-bulk',
  templateUrl: './software-details-bulk.component.html',
})
export class SoftwareDetailsBulkComponent {
  private readonly confirm = inject(ConfirmationService);
  private readonly actions = inject(ActionsService);
  protected readonly bulk = inject(SoftwareDetailsBulkService);
  protected readonly repository = inject(RepositoriesService);

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  private readonly keys$ = this.bulk.selection$.pipe(map((i) => i.map((x) => `${x.key.name}:${x.key.tag}`)));
  protected mappedDelete$ = this.actions.action(
    [Actions.DELETE_SOFTWARE, Actions.DELETE_PRODUCT],
    this.deleting$,
    null,
    this.keys$,
  );

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  protected onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} software packages/products?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> software packages/products. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
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
          .pipe(
            switchMap((resultDto) => this.confirm.prompt(BdBulkOperationResultConfirmationPromptComponent, resultDto)),
            finalize(() => this.deleting$.next(false)),
          )
          .subscribe();
      });
  }
}
