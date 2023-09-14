import { Component, inject } from '@angular/core';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryUsersService } from '../../../services/repository-users.service';

@Component({
  selector: 'app-software-repository-permissions',
  templateUrl: './permissions.component.html',
})
export class PermissionsComponent {
  protected repos = inject(RepositoriesService);
  protected users = inject(RepositoryUsersService);
}
