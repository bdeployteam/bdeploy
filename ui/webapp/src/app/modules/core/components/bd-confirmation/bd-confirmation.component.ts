import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-bd-confirmation',
  templateUrl: './bd-confirmation.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdConfirmationComponent {
  private dialogRef = inject(MatDialogRef<BdConfirmationComponent>);
  protected data: { header: string; message: string } = inject(MAT_DIALOG_DATA);

  confirm(answer: boolean) {
    this.dialogRef.close(answer);
  }
}
