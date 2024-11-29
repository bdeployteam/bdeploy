import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
    selector: 'app-state-status-column',
    templateUrl: './state-status-column.component.html',
    styleUrls: ['./state-status-column.component.css'],
    standalone: false
})
export class StateStatusColumnComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
