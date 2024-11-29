import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { UserInfo } from 'src/app/models/gen.dtos';

@Component({
    selector: 'app-add-user-to-group',
    templateUrl: './add-user-to-group.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class AddUserToGroupComponent {
  @Input() suggestedUsers: UserInfo[];
  @Output() userSelected = new EventEmitter<UserInfo>();

  protected userInput: string;

  protected addUserToGroup(): void {
    const selectedUser = this.suggestedUsers.find((u) => u.name === this.userInput);
    if (!selectedUser) return;
    this.userSelected.emit(selectedUser);
    this.userInput = ''; // clear input
  }

  protected get suggestions(): string[] {
    return this.suggestedUsers.map((u) => u.name);
  }
}
