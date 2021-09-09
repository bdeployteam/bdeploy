import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-date-cell',
  templateUrl: './bd-data-date-cell.component.html',
  styleUrls: ['./bd-data-date-cell.component.css'],
})
export class BdDataDateCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
