import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { CellComponent } from '../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

export interface PortStatus {
  status: boolean;
  message: string;
}

@Component({
    selector: 'app-rating-status-column',
    templateUrl: './rating-status-column.component.html',
    styleUrls: ['./rating-status-column.component.css'],
    imports: [MatIcon, MatTooltip]
})
export class RatingStatusColumnComponent<T> implements CellComponent<T, PortStatus> {
  @Input() record: T;
  @Input() column: BdDataColumn<T, PortStatus>;
}
