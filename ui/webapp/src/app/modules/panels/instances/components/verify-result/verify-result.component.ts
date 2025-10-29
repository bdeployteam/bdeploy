import { Component, OnInit, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { VerifyOperationResultDto } from 'src/app/models/gen.dtos';
import { BdNotificationCardComponent } from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { BdTerminalComponent } from '../../../../core/components/bd-terminal/bd-terminal.component';
import { BdActionRowComponent } from '../../../../core/components/bd-action-row/bd-action-row.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';

@Component({
  selector: 'app-verify-result',
  templateUrl: './verify-result.component.html',
  imports: [BdNotificationCardComponent, BdTerminalComponent, BdActionRowComponent, BdButtonComponent],
})
export class VerifyResultComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<VerifyResultComponent>);
  protected readonly data: VerifyOperationResultDto = inject(MAT_DIALOG_DATA);
  protected isCorrupted: boolean;

  protected verifyResult$ = new Subject<string>();

  ngOnInit(): void {
    if (this.data != null) {
      setTimeout(() => this.processResult(this.data), 0);
    }
  }

  private processResult(result: VerifyOperationResultDto) {
    this.isCorrupted = this.data.missingFiles.length > 0 || this.data.modifiedFiles.length > 0;

    if (!this.isCorrupted) {
      this.verifyResult$.next('No errors found.\n');
      return;
    }

    this.verifyResult$.next(`\nMissing Files: ${result.missingFiles.length}\n`);
    result.missingFiles.forEach((file) => this.verifyResult$.next(`${file}\n`));

    this.verifyResult$.next(`\nModified Files: ${result.modifiedFiles.length}\n`);
    result.modifiedFiles.forEach((file) => this.verifyResult$.next(`${file}\n`));

    this.verifyResult$.next(`\nRemaining ${result.unmodifiedFiles.length} files are unmodified.\n`);
  }

  confirm(answer: boolean) {
    this.dialogRef.close(answer);
  }
}
