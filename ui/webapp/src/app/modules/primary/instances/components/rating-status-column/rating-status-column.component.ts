import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'app-rating-status-column',
    templateUrl: './rating-status-column.component.html',
    styleUrls: ['./rating-status-column.component.css'],
    imports: [MatIcon, MatTooltip]
})
export class RatingStatusColumnComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
