import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'app-bd-data-permission-level-cell',
    templateUrl: './bd-data-permission-level-cell.component.html',
    styleUrls: ['./bd-data-permission-level-cell.component.css'],
    imports: [MatTooltip]
})
export class BdDataPermissionLevelCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
