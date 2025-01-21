import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { BdNotificationCardComponent } from '../bd-notification-card/bd-notification-card.component';
import { BdActionRowComponent } from '../bd-action-row/bd-action-row.component';
import { BdButtonComponent } from '../bd-button/bd-button.component';

@Component({
    selector: 'app-bd-confirmation',
    templateUrl: './bd-confirmation.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BdNotificationCardComponent, BdActionRowComponent, BdButtonComponent]
})
export class BdConfirmationComponent {
  private readonly dialogRef = inject(MatDialogRef<BdConfirmationComponent>);
  protected data: { header: string; message: string } = inject(MAT_DIALOG_DATA);

  confirm(answer: boolean) {
    this.dialogRef.close(answer);
  }
}
