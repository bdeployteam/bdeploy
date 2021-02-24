import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-bd-dialog-toolbar',
  templateUrl: './bd-dialog-toolbar.component.html',
  styleUrls: ['./bd-dialog-toolbar.component.css'],
})
export class BdDialogToolbarComponent implements OnInit {
  @Input() title: string;

  constructor() {}

  ngOnInit(): void {}
}
