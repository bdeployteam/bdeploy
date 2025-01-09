import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'app-state-status-column',
    templateUrl: './state-status-column.component.html',
    styleUrls: ['./state-status-column.component.css'],
    imports: [MatIcon, MatTooltip]
})
export class StateStatusColumnComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
