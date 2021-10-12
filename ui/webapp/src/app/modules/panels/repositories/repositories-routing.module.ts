import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AdminGuard } from '../../core/guards/admin.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { AddRepositoryComponent } from './components/add-repository/add-repository.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { MaintenanceComponent } from './components/settings/maintenance/maintenance.component';
import { SettingsComponent } from './components/settings/settings.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';

const REPOSITORIES_ROUTES: Route[] = [
  { path: 'add', component: AddRepositoryComponent, canActivate: [AdminGuard] },
  { path: 'settings', component: SettingsComponent, canActivate: [ScopedAdminGuard] },
  { path: 'settings/edit', component: EditComponent, canActivate: [ScopedWriteGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'settings/maintenance', component: MaintenanceComponent, canActivate: [ScopedAdminGuard] },
  { path: 'upload', component: SoftwareUploadComponent, canActivate: [ScopedWriteGuard] },
  { path: 'details/:key/:tag', component: SoftwareDetailsComponent, canActivate: [ScopedReadGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(REPOSITORIES_ROUTES)],
  exports: [RouterModule],
})
export class RepositoriesRoutingModule {}
