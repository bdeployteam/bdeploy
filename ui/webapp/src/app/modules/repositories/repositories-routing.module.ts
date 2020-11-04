import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AuthGuard } from '../shared/guards/authentication.guard';
import { CanDeactivateGuard } from '../shared/guards/can-deactivate.guard';
import { SoftwareRepoAddEditComponent } from './components/software-repo-add-edit/software-repo-add-edit.component';
import { SoftwareRepositoriesBrowserComponent } from './components/software-repositories-browser/software-repositories-browser.component';
import { SoftwareRepositoryPermissionsComponent } from './components/software-repository-permissions/software-repository-permissions.component';
import { SoftwareRepositoryComponent } from './components/software-repository/software-repository.component';

const REPO_ROUTES: Route[] = [
  {
    path: 'browser',
    component: SoftwareRepositoriesBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Software Repositories', header: 'Software Repositories' },
  },
  {
    path: 'add',
    component: SoftwareRepoAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Software Repository', header: 'Add Software Repository' },
  },
  {
    path: 'edit/:name',
    component: SoftwareRepoAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Software Repository (${params["name"]})', header: 'Edit Software Repository' },
  },
  {
    path: 'permissions/:name',
    component: SoftwareRepositoryPermissionsComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: {
      title: 'Manage Permissions of Software Repository (${params["name"]})',
      header: 'Software Repository Permissions',
    },
  },
  {
    path: 'packages/:name',
    component: SoftwareRepositoryComponent,
    canActivate: [AuthGuard],
    data: { title: 'Software Packages (${params["name"]})', header: 'Software Packages' },
  },
];

@NgModule({
  imports: [RouterModule.forChild(REPO_ROUTES)],
  exports: [RouterModule],
})
export class RepositoriesRoutingModule {}
