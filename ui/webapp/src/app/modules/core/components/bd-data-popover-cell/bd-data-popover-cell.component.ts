import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-popover-cell',
  templateUrl: './bd-data-popover-cell.component.html',
  styleUrls: ['./bd-data-popover-cell.component.css'],
})
export class BdDataPopoverCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
