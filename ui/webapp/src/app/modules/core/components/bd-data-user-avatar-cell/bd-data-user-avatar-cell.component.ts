import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
    selector: 'app-bd-data-user-avatar-cell',
    templateUrl: './bd-data-user-avatar-cell.component.html',
    standalone: false
})
export class BdDataUserAvatarCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
