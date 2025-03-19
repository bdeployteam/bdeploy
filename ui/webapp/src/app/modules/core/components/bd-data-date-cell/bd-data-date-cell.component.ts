import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { DatePipe } from '@angular/common';
import { CellComponent } from '../bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-bd-data-date-cell',
    templateUrl: './bd-data-date-cell.component.html',
    imports: [DatePipe]
})
export class BdDataDateCellComponent<T> implements CellComponent<T, number> {
  @Input() record: T;
  @Input() column: BdDataColumn<T, number>;
}
