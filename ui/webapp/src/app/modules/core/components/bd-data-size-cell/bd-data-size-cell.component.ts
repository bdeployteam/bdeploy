import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { formatSize } from '../../utils/object.utils';

@Component({
  selector: 'app-bd-data-size-cell',
  templateUrl: './bd-data-size-cell.component.html',
  styleUrls: ['./bd-data-size-cell.component.css'],
})
export class BdDataSizeCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  /* template */ formatted: string;

  constructor() {}

  ngOnInit(): void {
    const v = this.column.data(this.record);
    this.formatted = v === null || v === undefined ? '' : formatSize(v);
  }
}
