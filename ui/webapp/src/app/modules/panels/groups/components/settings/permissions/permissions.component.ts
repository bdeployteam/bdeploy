import { Component } from '@angular/core';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GroupUsersService } from '../../../services/group-users.service';

@Component({
  selector: 'app-permissions',
  templateUrl: './permissions.component.html',
})
export class PermissionsComponent {
  constructor(public groups: GroupsService, public users: GroupUsersService) {}
}
