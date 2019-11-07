import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-process-start-confirm',
  templateUrl: './process-start-confirm.component.html',
  styleUrls: ['./process-start-confirm.component.css'],
})
export class ProcessStartConfirmComponent implements OnInit {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    public dialogRef: MatDialogRef<ProcessStartConfirmComponent>,
  ) {}

  ngOnInit() {
    // Do not close if the user clicks anywhere on the screen
    this.dialogRef.disableClose = true;
  }

  okEnabled() {
    return this.data.confirmation === this.data.application;
  }

  onEnter() {
    if (!this.okEnabled()) {
      return;
    }
    this.dialogRef.close('start');
  }
}
