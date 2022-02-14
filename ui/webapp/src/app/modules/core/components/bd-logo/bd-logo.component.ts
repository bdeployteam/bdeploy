import { Component, Input, ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'app-bd-logo',
  templateUrl: './bd-logo.component.html',
  styleUrls: ['./bd-logo.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class BdLogoComponent {
  @Input() public size: number;
  @Input() public color: string;
}
