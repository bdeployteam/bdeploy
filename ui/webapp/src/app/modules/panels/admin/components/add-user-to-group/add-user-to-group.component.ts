import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { FormsModule } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-add-user-to-group',
    templateUrl: './add-user-to-group.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BdFormInputComponent, FormsModule, MatIcon]
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
