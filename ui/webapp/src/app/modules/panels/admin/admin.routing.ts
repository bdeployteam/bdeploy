import { Routes } from '@angular/router';

import { AdminGuard } from '../../core/guards/admin.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';


export const ADMIN_PANEL_ROUTES: Routes = [
  {
    path: 'add-plugin',
    loadComponent: () => import('./components/add-plugin/add-plugin.component').then(m => m.AddPluginComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'user-detail/:user',
    loadComponent: () => import('./components/user-admin-detail/user-admin-detail.component').then(m => m.UserAdminDetailComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'user-group-detail/:group',
    loadComponent: () => import('./components/user-group-admin-detail/user-group-admin-detail.component').then(m => m.UserGroupAdminDetailComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'add-node',
    loadComponent: () => import('./components/add-node/add-node.component').then(m => m.AddNodeComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'node-detail/:node',
    loadComponent: () => import('./components/node-details/node-details.component').then(m => m.NodeDetailsComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'node-detail/:node/edit',
    loadComponent: () => import('./components/node-details/node-edit/node-edit.component').then(m => m.NodeEditComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'node-detail/:node/replace',
    loadComponent: () => import('./components/node-details/node-edit/node-edit.component').then(m => m.NodeEditComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { replace: true },
  },
  {
    path: 'node-detail/:node/update',
    loadComponent: () => import('./components/node-details/node-update/node-update.component').then(m => m.NodeUpdateComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'node-detail/:node/maintenance',
    loadComponent: () => import('./components/node-details/node-maintenance/node-maintenance.component').then(m => m.NodeMaintenanceComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'node-detail/:node/conversion',
    loadComponent: () => import('./components/node-details/node-conversion/node-conversion.component').then(m => m.NodeConversionComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'add-multi-node',
    loadComponent: () => import('./components/add-multi-node/add-multi-node.component').then(m => m.AddMultiNodeComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard]
  },
  {
    path: 'multi-node-detail/:node',
    loadComponent: () => import('./components/multi-node-details/multi-node-details.component').then(m => m.MultiNodeDetailsComponent),
    canActivate: [AdminGuard]
  },
  {
    path: 'bhive/:bhive',
    loadComponent: () => import('./components/bhive-details/bhive-details.component').then(m => m.BhiveDetailsComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'bhive/:bhive/logs',
    loadComponent: () => import('./components/bhive-details/bhive-log-browser/bhive-log-browser.component').then(m => m.BhiveLogBrowserComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'bhive/:bhive/logs/:node/:file',
    loadComponent: () => import('./components/bhive-details/bhive-log-viewer/bhive-log-viewer.component').then(m => m.BhiveLogViewerComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'bhive/:bhive/browse/:type',
    loadComponent: () => import('../../core/components/bd-bhive-browser/bd-bhive-browser.component').then(m => m.BdBHiveBrowserComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'logging/view/:node/:file',
    loadComponent: () => import('./components/log-file-viewer/log-file-viewer.component').then(m => m.LogFileViewerComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'logging/config',
    loadComponent: () => import('./components/log-config-editor/log-config-editor.component').then(m => m.LogConfigEditorComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'software/upload',
    loadComponent: () => import('./components/software-upload/software-upload.component').then(m => m.SoftwareUploadComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'software/details/:version',
    loadComponent: () => import('./components/software-details/software-details.component').then(m => m.SoftwareDetailsComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'software/bulk-manipulation',
    loadComponent: () => import('./components/software-bulk-manipulation/software-bulk-manipulation.component').then(m => m.SoftwareBulkManipulationComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'add-ldap-server',
    loadComponent: () => import('./components/add-ldap-server/add-ldap-server.component').then(m => m.AddLdapServerComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'ldap/:id/edit',
    loadComponent: () => import('./components/edit-ldap-server/edit-ldap-server.component').then(m => m.EditLdapServerComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'ldap/:id/check',
    loadComponent: () => import('./components/check-ldap-server/check-ldap-server.component').then(m => m.CheckLdapServerComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'ldap/:id/import',
    loadComponent: () => import('./components/import-ldap-server/import-ldap-server.component').then(m => m.ImportLdapServerComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'mail-receiving',
    loadComponent: () => import('./components/mail-receiving/mail-receiving.component').then(m => m.MailReceivingComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'mail-sending',
    loadComponent: () => import('./components/mail-sending/mail-sending.component').then(m => m.MailSendingComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'auth-test',
    loadComponent: () => import('./components/auth-test/auth-test.component').then(m => m.AuthTestComponent),
    canActivate: [AdminGuard],
    data: { max: true },
  },
  {
    path: 'global-attribute-add',
    loadComponent: () => import('./components/add-global-attribute/add-global-attribute.component').then(m => m.AddGlobalAttributeComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'global-attribute/:attribute/edit',
    loadComponent: () => import('./components/edit-global-attribute/edit-global-attribute.component').then(m => m.EditGlobalAttributeComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'add-user',
    loadComponent: () => import('./components/add-user/add-user.component').then(m => m.AddUserComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-detail/:user/edit',
    loadComponent: () => import('./components/edit-user/edit-user.component').then(m => m.EditUserComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-detail/:user/permission',
    loadComponent: () => import('./components/assign-permission/assign-permission.component').then(m => m.AssignPermissionComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'add-user-group',
    loadComponent: () => import('./components/add-user-group/add-user-group.component').then(m => m.AddUserGroupComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-detail/:group/edit',
    loadComponent: () => import('./components/edit-user-group/edit-user-group.component').then(m => m.EditUserGroupComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-detail/:group/permission',
    loadComponent: () => import('./components/assign-user-group-permission/assign-user-group-permission.component').then(m => m.AssignUserGroupPermissionComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-bulk-manip',
    loadComponent: () => import('./components/user-bulk-manipulation/user-bulk-manipulation.component').then(m => m.UserBulkManipulationComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'user-bulk-manip/assign-permission',
    loadComponent: () => import('./components/user-bulk-assign-permission/user-bulk-assign-permission.component').then(m => m.UserBulkAssignPermissionComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-bulk-manip/remove-permission',
    loadComponent: () => import('./components/user-bulk-remove-permission/user-bulk-remove-permission.component').then(m => m.UserBulkRemovePermissionComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-bulk-manip/add-to-group',
    loadComponent: () => import('./components/user-bulk-add-to-group/user-bulk-add-to-group.component').then(m => m.UserBulkAddToGroupComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-bulk-manip',
    loadComponent: () => import('./components/user-group-bulk-manipulation/user-group-bulk-manipulation.component').then(m => m.UserGroupBulkManipulationComponent),
    canActivate: [AdminGuard],
  },
  {
    path: 'user-group-bulk-manip/assign-permission',
    loadComponent: () => import('./components/user-group-bulk-assign-permission/user-group-bulk-assign-permission.component').then(m => m.UserGroupBulkAssignPermissionComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'user-group-bulk-manip/remove-permission',
    loadComponent: () => import('./components/user-group-bulk-remove-permission/user-group-bulk-remove-permission.component').then(m => m.UserGroupBulkRemovePermissionComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];
