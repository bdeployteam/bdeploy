import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
    selector: 'app-port-status-column',
    templateUrl: './port-status-column.component.html',
    styleUrls: ['./port-status-column.component.css'],
    standalone: false
})
export class PortStatusColumnComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
