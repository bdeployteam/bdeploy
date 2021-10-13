import { Component, HostBinding, Input, OnInit, ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'app-bd-expand-button',
  templateUrl: './bd-expand-button.component.html',
  styleUrls: ['./bd-expand-button.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class BdExpandButtonComponent implements OnInit {
  @Input() icon: string;
  @HostBinding('attr.data-cy') @Input() text: string;
  @Input() expanded = false;

  constructor() {}

  ngOnInit(): void {}
}
