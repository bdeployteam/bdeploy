import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { CoreModule } from '../../core/core.module';
import { AddRepositoryComponent } from './components/add-repository/add-repository.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { PermissionsComponent } from './components/settings/permissions/permissions.component';
import { SettingsComponent } from './components/settings/settings.component';
import { UserGroupPermissionsComponent } from './components/settings/user-group-permissions/user-group-permissions.component';
import { UserPermissionsComponent } from './components/settings/user-permissions/user-permissions.component';
import { SoftwareDetailsBulkComponent } from './components/software-details-bulk/software-details-bulk.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';
import { RepositoriesRoutingModule } from './repositories-routing.module';

@NgModule({
  declarations: [
    AddRepositoryComponent,
    SettingsComponent,
    EditComponent,
    PermissionsComponent,
    UserPermissionsComponent,
    UserGroupPermissionsComponent,
    SoftwareUploadComponent,
    SoftwareDetailsComponent,
    SoftwareDetailsBulkComponent,
  ],
  imports: [CommonModule, CoreModule, RepositoriesRoutingModule, MatTabsModule],
})
export class RepositoriesModule {}
