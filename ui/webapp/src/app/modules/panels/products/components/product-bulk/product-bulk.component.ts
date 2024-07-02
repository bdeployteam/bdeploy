import { Component, ViewChild, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfirmationService } from 'src/app/modules/core/services/confirmation.service';
import { ProductBulkService } from '../../services/product-bulk.service';
import { ProductBulkOperationResultComponent } from '../product-bulk-operation-result/product-bulk-operation-result.component';

@Component({
  selector: 'app-product-bulk',
  templateUrl: './product-bulk.component.html',
})
export class ProductBulkComponent {
  protected bulk = inject(ProductBulkService);
  private readonly confirm = inject(ConfirmationService);

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
            switchMap((resultDto) => this.confirm.prompt(ProductBulkOperationResultComponent, resultDto)),
            finalize(() => this.deleting$.next(false)),
          )
          .subscribe();
      });
  }
}
