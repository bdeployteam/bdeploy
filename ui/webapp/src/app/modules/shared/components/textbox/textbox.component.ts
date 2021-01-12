import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

export interface TextboxData {
  /** the title of the text box */
  title: string;
  /** the actual content of the text box. */
  text: string;
}

@Component({
  selector: 'app-textbox',
  templateUrl: './textbox.component.html',
  styleUrls: ['./textbox.component.css'],
})
export class TextboxComponent implements OnInit {
  constructor(private dialogRef: MatDialogRef<TextboxComponent>, @Inject(MAT_DIALOG_DATA) public data: TextboxData) {}

  ngOnInit() {}
}
