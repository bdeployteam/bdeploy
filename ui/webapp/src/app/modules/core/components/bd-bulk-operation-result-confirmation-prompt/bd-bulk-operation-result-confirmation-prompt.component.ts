import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';
import { BdNotificationCardComponent } from '../bd-notification-card/bd-notification-card.component';
import { MatIcon } from '@angular/material/icon';
import { BdActionRowComponent } from '../bd-action-row/bd-action-row.component';
import { BdButtonComponent } from '../bd-button/bd-button.component';

@Component({
    selector: 'app-bd-bulk-operation-result-confirmation-prompt',
    templateUrl: './bd-bulk-operation-result-confirmation-prompt.component.html',
    imports: [BdNotificationCardComponent, MatIcon, BdActionRowComponent, BdButtonComponent]
})
export class BdBulkOperationResultConfirmationPromptComponent {
  private readonly dialogRef = inject(MatDialogRef<BulkOperationResultDto>);
  protected readonly bulkOpResult: BulkOperationResultDto = inject(MAT_DIALOG_DATA);

  close() {
    this.dialogRef.close(false);
  }
}
