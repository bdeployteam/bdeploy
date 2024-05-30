import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-product-bulk-operation-result',
  templateUrl: './product-bulk-operation-result.component.html',
})
export class ProductBulkOperationResultComponent {
  private dialogRef = inject(MatDialogRef<BulkOperationResultDto>);
  protected bulkOpResult: BulkOperationResultDto = inject(MAT_DIALOG_DATA);

  close() {
    this.dialogRef.close(false);
  }
}
