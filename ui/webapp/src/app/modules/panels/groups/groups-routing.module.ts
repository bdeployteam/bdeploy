import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AdminGuard } from '../../core/guards/admin.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { PanelScopedAdminGuard } from '../../core/guards/panel-scoped-admin.guard';
import { PanelScopedWriteGuard } from '../../core/guards/panel-scoped-write.guard';
import { AddGroupComponent } from './components/add-group/add-group.component';
import { ClientDetailComponent } from './components/client-detail/client-detail.component';
import { AttributeDefinitionsComponent } from './components/settings/attribute-definitions/attribute-definitions.component';
import { AttributeValuesComponent } from './components/settings/attribute-values/attribute-values.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { MaintenanceComponent } from './components/settings/maintenance/maintenance.component';
import { SettingsComponent } from './components/settings/settings.component';

const GROUPS_ROUTES: Route[] = [
  { path: 'add', component: AddGroupComponent, canActivate: [AdminGuard] },
  { path: 'settings', component: SettingsComponent, canActivate: [PanelScopedWriteGuard] },
  { path: 'settings/edit', component: EditComponent, canActivate: [PanelScopedAdminGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'settings/attributes/values', component: AttributeValuesComponent, canActivate: [PanelScopedWriteGuard] },
  { path: 'settings/attributes/definitions', component: AttributeDefinitionsComponent, canActivate: [PanelScopedAdminGuard] },
  { path: 'settings/maintenance', component: MaintenanceComponent, canActivate: [PanelScopedWriteGuard] },
  { path: 'client/:app', component: ClientDetailComponent },
];

@NgModule({
  imports: [RouterModule.forChild(GROUPS_ROUTES)],
  exports: [RouterModule],
})
export class GroupsRoutingModule {}
