import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'app-port-status-column',
    templateUrl: './port-status-column.component.html',
    styleUrls: ['./port-status-column.component.css'],
    imports: [MatIcon, MatTooltip]
})
export class PortStatusColumnComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
