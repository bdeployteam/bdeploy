import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-add-user-to-group',
  templateUrl: './add-user-to-group.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddUserToGroupComponent {
  @Input() group: string;
  @Input() suggestedUsers: UserInfo[];
  @Output() userSelected = new EventEmitter<UserInfo>();

  /* template */ user: string;

  /* template */ public addUserToGroup(): void {
    this.userSelected.emit(this.selectedUser);
  }

  /* template */ get suggestions(): string[] {
    return this.suggestedUsers.map((u) => u.name);
  }

  /* template */ get selectedUser(): UserInfo {
    return this.suggestedUsers.find((u) => u.name === this.user);
  }
}
