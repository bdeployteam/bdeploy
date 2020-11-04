import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

export interface VariableInput {
  uid: string;
  name: string;
  value: string;
  description: string;
  suggestedValues: string[];
}

@Component({
  selector: 'app-application-template-variable-dialog',
  templateUrl: './application-template-variable-dialog.component.html',
  styleUrls: ['./application-template-variable-dialog.component.css'],
})
export class ApplicationTemplateVariableDialogComponent implements OnInit {
  constructor(@Inject(MAT_DIALOG_DATA) public variables: VariableInput[]) {}

  ngOnInit(): void {}
}
