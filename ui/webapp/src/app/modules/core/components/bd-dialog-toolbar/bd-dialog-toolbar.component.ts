import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-bd-dialog-toolbar',
  templateUrl: './bd-dialog-toolbar.component.html',
  styleUrls: ['./bd-dialog-toolbar.component.css'],
})
export class BdDialogToolbarComponent implements OnInit {
  @Input() header: string;
  @Input() closeablePanel = false;

  constructor() {}

  ngOnInit(): void {}
}
