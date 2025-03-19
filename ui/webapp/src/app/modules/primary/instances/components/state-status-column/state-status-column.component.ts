import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { CellComponent } from '../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-state-status-column',
    templateUrl: './state-status-column.component.html',
    styleUrls: ['./state-status-column.component.css'],
    imports: [MatIcon, MatTooltip]
})
export class StateStatusColumnComponent<T> implements CellComponent<T, boolean> {
  @Input() record: T;
  @Input() column: BdDataColumn<T, boolean>;
}
