import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { UserAvatarComponent } from '../user-avatar/user-avatar.component';

@Component({
    selector: 'app-bd-data-user-avatar-cell',
    templateUrl: './bd-data-user-avatar-cell.component.html',
    imports: [UserAvatarComponent]
})
export class BdDataUserAvatarCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
