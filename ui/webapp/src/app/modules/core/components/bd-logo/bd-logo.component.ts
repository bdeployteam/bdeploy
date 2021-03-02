import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'app-bd-logo',
  templateUrl: './bd-logo.component.html',
  styleUrls: ['./bd-logo.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class BdLogoComponent implements OnInit {
  @Input() public size: number;
  @Input() public color: string;

  constructor() {}

  ngOnInit() {}

  toolbarClass(): string {
    if (this.color === 'main-toolbar') {
      return 'local-toolbar-color';
    }
  }
}
