import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { DatePipe } from '@angular/common';

@Component({
    selector: 'app-bd-data-date-cell',
    templateUrl: './bd-data-date-cell.component.html',
    imports: [DatePipe]
})
export class BdDataDateCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
