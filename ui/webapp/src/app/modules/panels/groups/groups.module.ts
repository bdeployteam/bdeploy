import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AddGroupComponent } from './components/add-group/add-group.component';
import { ClientDetailComponent } from './components/client-detail/client-detail.component';
import { AttributeDefinitionsComponent } from './components/settings/attribute-definitions/attribute-definitions.component';
import { AttributeValuesComponent } from './components/settings/attribute-values/attribute-values.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { MaintenanceComponent } from './components/settings/maintenance/maintenance.component';
import { PermissionLevelComponent } from './components/settings/permissions/permission-level/permission-level.component';
import { PermissionsComponent } from './components/settings/permissions/permissions.component';
import { SettingsComponent } from './components/settings/settings.component';
import { GroupsRoutingModule } from './groups-routing.module';

@NgModule({
  declarations: [
    AddGroupComponent,
    SettingsComponent,
    EditComponent,
    AttributeValuesComponent,
    AttributeDefinitionsComponent,
    MaintenanceComponent,
    ClientDetailComponent,
    PermissionsComponent,
    PermissionLevelComponent,
  ],
  imports: [CommonModule, CoreModule, GroupsRoutingModule],
})
export class GroupsModule {}
