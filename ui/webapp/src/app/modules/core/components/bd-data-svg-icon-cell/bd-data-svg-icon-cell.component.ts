import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';
import { CellComponent } from '../bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-bd-data-svg-icon-cell',
    templateUrl: './bd-data-svg-icon-cell.component.html',
    imports: [MatIcon]
})
export class BdDataSvgIconCellComponent<T> implements CellComponent<T, string>{
  @Input() record: T;
  @Input() column: BdDataColumn<T, string>;
}
