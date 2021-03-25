import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-boolean-cell',
  templateUrl: './bd-data-boolean-cell.component.html',
  styleUrls: ['./bd-data-boolean-cell.component.css'],
})
export class BdDataBooleanCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
