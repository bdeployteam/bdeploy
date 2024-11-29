import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
    selector: 'app-bd-data-sync-cell',
    templateUrl: './bd-data-sync-cell.component.html',
    standalone: false
})
export class BdDataSyncCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
