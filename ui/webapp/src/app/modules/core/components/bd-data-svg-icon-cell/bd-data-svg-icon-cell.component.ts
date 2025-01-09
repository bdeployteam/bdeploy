import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-bd-data-svg-icon-cell',
    templateUrl: './bd-data-svg-icon-cell.component.html',
    imports: [MatIcon]
})
export class BdDataSvgIconCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
