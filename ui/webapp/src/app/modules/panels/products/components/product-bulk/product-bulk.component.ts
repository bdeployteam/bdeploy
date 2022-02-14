import { Component, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ProductBulkService } from '../../services/product-bulk.service';

@Component({
  selector: 'app-product-bulk',
  templateUrl: './product-bulk.component.html',
})
export class ProductBulkComponent {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(public bulk: ProductBulkService) {}

  /* template */ onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} products?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> product versions. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
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
          .subscribe();
      });
  }
}
