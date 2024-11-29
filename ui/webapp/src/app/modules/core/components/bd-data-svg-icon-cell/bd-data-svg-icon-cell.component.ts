import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
    selector: 'app-bd-data-svg-icon-cell',
    templateUrl: './bd-data-svg-icon-cell.component.html',
    standalone: false
})
export class BdDataSvgIconCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
