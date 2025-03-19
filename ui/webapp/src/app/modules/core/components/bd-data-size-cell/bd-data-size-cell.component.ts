import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { formatSize } from '../../utils/object.utils';
import { CellComponent } from '../bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-bd-data-size-cell',
    templateUrl: './bd-data-size-cell.component.html'
})
export class BdDataSizeCellComponent<T> implements OnInit, CellComponent<T, number> {
  @Input() record: T;
  @Input() column: BdDataColumn<T, number>;

  protected formatted: string;

  ngOnInit(): void {
    const v = this.column.data(this.record);
    this.formatted = v === null || v === undefined ? '' : formatSize(v);
  }
}
