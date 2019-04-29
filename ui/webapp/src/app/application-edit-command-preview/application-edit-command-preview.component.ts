import { Component, Inject, OnInit } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA } from '@angular/material';

@Component({
  selector: 'app-application-edit-command-preview',
  templateUrl: './application-edit-command-preview.component.html',
  styleUrls: ['./application-edit-command-preview.component.css'],
})
export class ApplicationEditCommandPreviewComponent implements OnInit {
  public commandLinePreview: string[];

  constructor(@Inject(MAT_BOTTOM_SHEET_DATA) public data: any) {
    this.commandLinePreview = data.commandLinePreview;
  }

  ngOnInit() {}
}
