import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { BdIdentifierComponent } from '../bd-identifier/bd-identifier.component';
import { CellComponent } from '../bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-bd-identifier-cell',
    template: '<app-bd-identifier [id]="column.data(record)"></app-bd-identifier>',
    imports: [BdIdentifierComponent]
})
export class BdIdentifierCellComponent<T> implements CellComponent<T, string> {
  @Input() record: T;
  @Input() column: BdDataColumn<T, string>;
}
