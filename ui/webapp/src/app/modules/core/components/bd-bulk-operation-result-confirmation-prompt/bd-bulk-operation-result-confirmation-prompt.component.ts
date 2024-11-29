import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';

@Component({
    selector: 'app-bd-bulk-operation-result-confirmation-prompt',
    templateUrl: './bd-bulk-operation-result-confirmation-prompt.component.html',
    standalone: false
})
export class BdBulkOperationResultConfirmationPromptComponent {
  private readonly dialogRef = inject(MatDialogRef<BulkOperationResultDto>);
  protected readonly bulkOpResult: BulkOperationResultDto = inject(MAT_DIALOG_DATA);

  close() {
    this.dialogRef.close(false);
  }
}
