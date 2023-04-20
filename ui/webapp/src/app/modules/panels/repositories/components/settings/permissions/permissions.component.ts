import { Component } from '@angular/core';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryUsersService } from '../../../services/repository-users.service';

@Component({
  selector: 'app-permissions',
  templateUrl: './permissions.component.html',
})
export class PermissionsComponent {
  constructor(
    public repos: RepositoriesService,
    public users: RepositoryUsersService
  ) {}
}
