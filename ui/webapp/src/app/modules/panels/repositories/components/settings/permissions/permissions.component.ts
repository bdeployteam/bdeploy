import { Component, inject } from '@angular/core';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryUsersService } from '../../../services/repository-users.service';
import { BdDialogComponent } from '../../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatTabGroup, MatTab } from '@angular/material/tabs';
import { UserPermissionsComponent } from '../user-permissions/user-permissions.component';
import { UserGroupPermissionsComponent } from '../user-group-permissions/user-group-permissions.component';

@Component({
    selector: 'app-software-repository-permissions',
    templateUrl: './permissions.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, MatTabGroup, MatTab, UserPermissionsComponent, UserGroupPermissionsComponent]
})
export class PermissionsComponent {
  protected readonly repos = inject(RepositoriesService);
  protected readonly users = inject(RepositoryUsersService);
}
