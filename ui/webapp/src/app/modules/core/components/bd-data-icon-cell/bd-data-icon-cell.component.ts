import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-icon-cell',
  templateUrl: './bd-data-icon-cell.component.html',
})
export class BdDataIconCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
