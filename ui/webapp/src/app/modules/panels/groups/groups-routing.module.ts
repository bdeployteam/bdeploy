import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AdminGuard } from '../../core/guards/admin.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { AddGroupComponent } from './components/add-group/add-group.component';
import { ClientDetailComponent } from './components/client-detail/client-detail.component';
import { ProcessUiInlineComponent } from './components/process-ui-inline/process-ui-inline.component';
import { AttributeDefinitionsComponent } from './components/settings/attribute-definitions/attribute-definitions.component';
import { AttributeValuesComponent } from './components/settings/attribute-values/attribute-values.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { PermissionsComponent } from './components/settings/permissions/permissions.component';
import { SettingsComponent } from './components/settings/settings.component';

const GROUPS_ROUTES: Route[] = [
  {
    path: 'add',
    component: AddGroupComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings',
    component: SettingsComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/edit',
    component: EditComponent,
    canActivate: [ScopedAdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings/attributes/values',
    component: AttributeValuesComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/attributes/definitions',
    component: AttributeDefinitionsComponent,
    canActivate: [ScopedAdminGuard],
  },
  {
    path: 'settings/permissions',
    component: PermissionsComponent,
    canActivate: [ScopedAdminGuard],
    data: { max: true },
  },
  {
    path: 'client/:app',
    component: ClientDetailComponent,
  },
  {
    path: 'endpoint/:app/:endpoint',
    component: ProcessUiInlineComponent,
    data: { max: true },
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(GROUPS_ROUTES))],
  exports: [RouterModule],
})
export class GroupsRoutingModule {}
