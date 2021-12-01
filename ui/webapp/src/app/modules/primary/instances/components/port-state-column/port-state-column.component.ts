import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-port-state-column',
  templateUrl: './port-state-column.component.html',
  styleUrls: ['./port-state-column.component.css'],
})
export class PortStateColumnComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
