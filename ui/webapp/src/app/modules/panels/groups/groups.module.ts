import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AddGroupComponent } from './components/add-group/add-group.component';
import { GroupsRoutingModule } from './groups-routing.module';
import { SettingsComponent } from './components/settings/settings.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { AttributeValuesComponent } from './components/settings/attribute-values/attribute-values.component';
import { AttributeDefinitionsComponent } from './components/settings/attribute-definitions/attribute-definitions.component';
import { MaintenanceComponent } from './components/settings/maintenance/maintenance.component';
import { ClientDetailComponent } from './components/client-detail/client-detail.component';
import { PermissionsComponent } from './components/settings/permissions/permissions.component';
import { PermissionLevelComponent } from './components/settings/permissions/permission-level/permission-level.component';
import { UserSelectOptionComponent } from './components/settings/permissions/user-select-option/user-select-option.component';

@NgModule({
  declarations: [AddGroupComponent, SettingsComponent, EditComponent, AttributeValuesComponent, AttributeDefinitionsComponent, MaintenanceComponent, ClientDetailComponent, PermissionsComponent, PermissionLevelComponent, UserSelectOptionComponent],
  imports: [CommonModule, CoreModule, GroupsRoutingModule],
})
export class GroupsModule {}
