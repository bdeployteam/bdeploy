import { Component, inject, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
import {
  BdBulkOperationResultConfirmationPromptComponent
} from 'src/app/modules/core/components/bd-bulk-operation-result-confirmation-prompt/bd-bulk-operation-result-confirmation-prompt.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfirmationService } from 'src/app/modules/core/services/confirmation.service';
import { ProductBulkService } from '../../services/product-bulk.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-product-bulk',
    templateUrl: './product-bulk.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, MatDivider, BdButtonComponent, AsyncPipe]
})
export class ProductBulkComponent {
  private readonly confirm = inject(ConfirmationService);
  protected readonly bulk = inject(ProductBulkService);

  protected deleting$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  protected onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} products?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> product versions. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
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
