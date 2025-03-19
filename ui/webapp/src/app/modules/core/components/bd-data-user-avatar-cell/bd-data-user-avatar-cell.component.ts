import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { UserAvatarComponent } from '../user-avatar/user-avatar.component';
import { CellComponent } from '../bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-bd-data-user-avatar-cell',
    templateUrl: './bd-data-user-avatar-cell.component.html',
    imports: [UserAvatarComponent]
})
export class BdDataUserAvatarCellComponent<T> implements CellComponent<T, string> {
  @Input() record: T;
  @Input() column: BdDataColumn<T, string>;
}
