import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-bd-confirmation',
  templateUrl: './bd-confirmation.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdConfirmationComponent {
  constructor(
    private dialogRef: MatDialogRef<BdConfirmationComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { header: string; message: string }
  ) {}

  confirm(answer: boolean) {
    this.dialogRef.close(answer);
  }
}
