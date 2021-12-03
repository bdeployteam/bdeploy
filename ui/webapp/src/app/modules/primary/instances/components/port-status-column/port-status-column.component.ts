import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-port-status-column',
  templateUrl: './port-status-column.component.html',
  styleUrls: ['./port-status-column.component.css'],
})
export class PortStatusColumnComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
