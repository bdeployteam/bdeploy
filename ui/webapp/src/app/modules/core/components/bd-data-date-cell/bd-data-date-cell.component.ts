import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
    selector: 'app-bd-data-date-cell',
    templateUrl: './bd-data-date-cell.component.html',
    standalone: false
})
export class BdDataDateCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
