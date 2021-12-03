import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-state-status-column',
  templateUrl: './state-status-column.component.html',
  styleUrls: ['./state-status-column.component.css'],
})
export class StateStatusColumnComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
