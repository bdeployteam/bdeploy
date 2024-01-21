import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { BdBHiveBrowserComponent } from '../../core/components/bd-bhive-browser/bd-bhive-browser.component';
import { AdminGuard } from '../../core/guards/admin.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { AddGlobalAttributeComponent } from './components/add-global-attribute/add-global-attribute.component';
import { AddLdapServerComponent } from './components/add-ldap-server/add-ldap-server.component';
import { AddNodeComponent } from './components/add-node/add-node.component';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { AddUserGroupComponent } from './components/add-user-group/add-user-group.component';
import { AddUserComponent } from './components/add-user/add-user.component';
import { AssignPermissionComponent } from './components/assign-permission/assign-permission.component';
import { AssignUserGroupPermissionComponent } from './components/assign-user-group-permission/assign-user-group-permission.component';
import { AuthTestComponent } from './components/auth-test/auth-test.component';
import { BhiveDetailsComponent as BHiveDetailsComponent } from './components/bhive-details/bhive-details.component';
import { BhiveLogBrowserComponent } from './components/bhive-details/bhive-log-browser/bhive-log-browser.component';
import { BhiveLogViewerComponent } from './components/bhive-details/bhive-log-viewer/bhive-log-viewer.component';
import { CheckLdapServerComponent } from './components/check-ldap-server/check-ldap-server.component';
import { EditGlobalAttributeComponent } from './components/edit-global-attribute/edit-global-attribute.component';
import { EditLdapServerComponent } from './components/edit-ldap-server/edit-ldap-server.component';
import { EditUserGroupComponent } from './components/edit-user-group/edit-user-group.component';
import { EditUserComponent } from './components/edit-user/edit-user.component';
import { ImportLdapServerComponent } from './components/import-ldap-server/import-ldap-server.component';
import { LogConfigEditorComponent } from './components/log-config-editor/log-config-editor.component';
import { LogFileViewerComponent } from './components/log-file-viewer/log-file-viewer.component';
import { NodeConversionComponent } from './components/node-details/node-conversion/node-conversion.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { NodeEditComponent } from './components/node-details/node-edit/node-edit.component';
import { NodeMaintenanceComponent } from './components/node-details/node-maintenance/node-maintenance.component';
import { NodeUpdateComponent } from './components/node-details/node-update/node-update.component';
import { SoftwareBulkManipulationComponent } from './components/software-bulk-manipulation/software-bulk-manipulation.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';
import { UserAdminDetailComponent } from './components/user-admin-detail/user-admin-detail.component';
import { UserBulkAddToGroupComponent } from './components/user-bulk-add-to-group/user-bulk-add-to-group.component';
import { UserBulkAssignPermissionComponent } from './components/user-bulk-assign-permission/user-bulk-assign-permission.component';
import { UserBulkManipulationComponent } from './components/user-bulk-manipulation/user-bulk-manipulation.component';
import { UserBulkRemovePermissionComponent } from './components/user-bulk-remove-permission/user-bulk-remove-permission.component';
import { UserGroupAdminDetailComponent } from './components/user-group-admin-detail/user-group-admin-detail.component';
import { UserGroupBulkAssignPermissionComponent } from './components/user-group-bulk-assign-permission/user-group-bulk-assign-permission.component';
import { UserGroupBulkManipulationComponent } from './components/user-group-bulk-manipulation/user-group-bulk-manipulation.component';
import { UserGroupBulkRemovePermissionComponent } from './components/user-group-bulk-remove-permission/user-group-bulk-remove-permission.component';

const routes: Routes = [
  {
    path: 'add-plugin',
    component: AddPluginComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'user-detail/:user',
    component: UserAdminDetailComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'user-group-detail/:group',
    component: UserGroupAdminDetailComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'add-node',
    component: AddNodeComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'node-detail/:node',
    component: NodeDetailsComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'node-detail/:node/edit',
    component: NodeEditComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'node-detail/:node/replace',
    component: NodeEditComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { replace: true },
  },
  {
    path: 'node-detail/:node/update',
    component: NodeUpdateComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'node-detail/:node/maintenance',
    component: NodeMaintenanceComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'node-detail/:node/conversion',
    component: NodeConversionComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'bhive/:bhive',
    component: BHiveDetailsComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'bhive/:bhive/logs',
    component: BhiveLogBrowserComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'bhive/:bhive/logs/:node/:file',
    component: BhiveLogViewerComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'bhive/:bhive/browse/:type',
    component: BdBHiveBrowserComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'logging/view/:node/:file',
    component: LogFileViewerComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'logging/config',
    component: LogConfigEditorComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'software/upload',
    component: SoftwareUploadComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'software/details/:version',
    component: SoftwareDetailsComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'software/bulk-manipulation',
    component: SoftwareBulkManipulationComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'add-ldap-server',
    component: AddLdapServerComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'ldap/:id/edit',
    component: EditLdapServerComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'ldap/:id/check',
    component: CheckLdapServerComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'ldap/:id/import',
    component: ImportLdapServerComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'auth-test',
    component: AuthTestComponent,
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'global-attribute-add',
    component: AddGlobalAttributeComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'global-attribute/:attribute/edit',
    component: EditGlobalAttributeComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'add-user',
    component: AddUserComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-detail/:user/edit',
    component: EditUserComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-detail/:user/permission',
    component: AssignPermissionComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'add-user-group',
    component: AddUserGroupComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-detail/:group/edit',
    component: EditUserGroupComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-detail/:group/permission',
    component: AssignUserGroupPermissionComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-bulk-manip',
    component: UserBulkManipulationComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'user-bulk-manip/assign-permission',
    component: UserBulkAssignPermissionComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-bulk-manip/remove-permission',
    component: UserBulkRemovePermissionComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-bulk-manip/add-to-group',
    component: UserBulkAddToGroupComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-bulk-manip',
    component: UserGroupBulkManipulationComponent,
    canActivate: [AdminGuard],
  },
  {
    path: 'user-group-bulk-manip/assign-permission',
    component: UserGroupBulkAssignPermissionComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-bulk-manip/remove-permission',
    component: UserGroupBulkRemovePermissionComponent,
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(routes))],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
