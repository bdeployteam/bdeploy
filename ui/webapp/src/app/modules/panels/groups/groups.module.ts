import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { CoreModule } from '../../core/core.module';
import { AddGroupComponent } from './components/add-group/add-group.component';
import { ClientDetailComponent } from './components/client-detail/client-detail.component';
import { ClientUsageGraphComponent } from './components/client-detail/usage-graph/usage-graph.component';
import { EndpointDetailComponent } from './components/endpoint-detail/endpoint-detail.component';
import { ProcessUiInlineComponent } from './components/process-ui-inline/process-ui-inline.component';
import { AttributeDefinitionsComponent } from './components/settings/attribute-definitions/attribute-definitions.component';
import { AttributeValuesComponent } from './components/settings/attribute-values/attribute-values.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { PermissionsComponent } from './components/settings/permissions/permissions.component';
import { SettingsComponent } from './components/settings/settings.component';
import { UserGroupPermissionsComponent } from './components/settings/user-group-permissions/user-group-permissions.component';
import { UserPermissionsComponent } from './components/settings/user-permissions/user-permissions.component';
import { GroupsRoutingModule } from './groups-routing.module';

@NgModule({
  declarations: [
    AddGroupComponent,
    SettingsComponent,
    EditComponent,
    AttributeValuesComponent,
    AttributeDefinitionsComponent,
    ClientDetailComponent,
    EndpointDetailComponent,
    PermissionsComponent,
    UserPermissionsComponent,
    UserGroupPermissionsComponent,
    ClientUsageGraphComponent,
    ProcessUiInlineComponent,
  ],
  imports: [CommonModule, CoreModule, GroupsRoutingModule, MatTabsModule],
})
export class GroupsModule {}
