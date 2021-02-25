import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-bd-action-row',
  templateUrl: './bd-action-row.component.html',
  styleUrls: ['./bd-action-row.component.css'],
})
export class BdActionRowComponent implements OnInit {
  @Input() align: 'left' | 'right' = 'right';

  constructor() {}

  ngOnInit(): void {}
}
