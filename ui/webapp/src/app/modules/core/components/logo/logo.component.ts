import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-logo',
  templateUrl: './logo.component.html',
  styleUrls: ['./logo.component.css'],
})
export class LogoComponent implements OnInit {
  @Input() public size: number;
  @Input() public color: string;

  constructor() {}

  ngOnInit() {}

  toolbarClass(): string {
    if (this.color === 'main-toolbar') {
      return 'logo-toolbar-color';
    }
  }
}
