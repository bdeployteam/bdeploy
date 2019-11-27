import { Component, OnInit, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

export enum MessageBoxMode {
  QUESTION,
  CONFIRM,
  CONFIRM_WARNING,
  INFO,
  WARNING,
  ERROR
}

export interface MessageBoxData {
  /** the title of the message box */
  title: string;
  /** the actual content of the messagebox. HTML is allowed. */
  message: string;
  /** the mode of the messagebox, determines buttons and icon */
  mode: MessageBoxMode;
}

@Component({
  selector: 'app-messagebox',
  templateUrl: './messagebox.component.html',
  styleUrls: ['./messagebox.component.css']
})
export class MessageboxComponent implements OnInit {

  constructor(
    private dialogRef: MatDialogRef<MessageboxComponent>,
    @Inject(MAT_DIALOG_DATA) public data: MessageBoxData
  ) { }

  ngOnInit() {
  }

  public requiresCancel(): boolean {
    return this.data.mode === MessageBoxMode.CONFIRM || this.data.mode === MessageBoxMode.CONFIRM_WARNING;
  }

  public requiresOk(): boolean {
    return this.data.mode !== MessageBoxMode.QUESTION;
  }

  public requiresYesNo(): boolean {
    return this.data.mode === MessageBoxMode.QUESTION;
  }

  public isWarning(): boolean {
    return this.data.mode === MessageBoxMode.WARNING || this.data.mode === MessageBoxMode.CONFIRM_WARNING;
  }

  public isError(): boolean {
    return this.data.mode === MessageBoxMode.ERROR;
  }

}
