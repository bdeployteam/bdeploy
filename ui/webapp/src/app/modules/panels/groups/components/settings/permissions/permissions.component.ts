import { Component, inject } from '@angular/core';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GroupUsersService } from '../../../services/group-users.service';

@Component({
  selector: 'app-instance-group-permissions',
  templateUrl: './permissions.component.html',
})
export class PermissionsComponent {
  protected readonly groups = inject(GroupsService);
  protected readonly users = inject(GroupUsersService);
}
