import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { PasswordStrengthMeterModule } from 'angular-password-strength-meter';
import { CoreModule } from '../../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AddGlobalAttributeComponent } from './components/add-global-attribute/add-global-attribute.component';
import { AddLdapServerComponent } from './components/add-ldap-server/add-ldap-server.component';
import { AddNodeComponent } from './components/add-node/add-node.component';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { AddUserGroupComponent } from './components/add-user-group/add-user-group.component';
import { AddUserToGroupComponent } from './components/add-user-to-group/add-user-to-group.component';
import { AddUserComponent } from './components/add-user/add-user.component';
import { AssignPermissionComponent } from './components/assign-permission/assign-permission.component';
import { AssignUserGroupPermissionComponent } from './components/assign-user-group-permission/assign-user-group-permission.component';
import { AuthTestComponent } from './components/auth-test/auth-test.component';
import { BHiveBrowserComponent } from './components/bhive-details/bhive-browser/bhive-browser.component';
import { ManifestDeleteActionComponent } from './components/bhive-details/bhive-browser/manifest-delete-action/manifest-delete-action.component';
import { BhiveDetailsComponent } from './components/bhive-details/bhive-details.component';
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
import { UserGroupAdminDetailComponent } from './components/user-group-admin-detail/user-group-admin-detail.component';
import { UserGroupBulkAssignPermissionComponent } from './components/user-group-bulk-assign-permission/user-group-bulk-assign-permission.component';
import { UserGroupBulkManipulationComponent } from './components/user-group-bulk-manipulation/user-group-bulk-manipulation.component';

@NgModule({
  declarations: [
    AddPluginComponent,
    UserAdminDetailComponent,
    UserGroupAdminDetailComponent,
    BhiveDetailsComponent,
    BHiveBrowserComponent,
    BhiveLogViewerComponent,
    BhiveLogBrowserComponent,
    ManifestDeleteActionComponent,
    LogFileViewerComponent,
    LogConfigEditorComponent,
    SoftwareUploadComponent,
    SoftwareDetailsComponent,
    SoftwareBulkManipulationComponent,
    AddLdapServerComponent,
    EditLdapServerComponent,
    CheckLdapServerComponent,
    ImportLdapServerComponent,
    AuthTestComponent,
    AddGlobalAttributeComponent,
    EditGlobalAttributeComponent,
    AddUserComponent,
    AddUserGroupComponent,
    EditUserComponent,
    EditUserGroupComponent,
    AssignPermissionComponent,
    AssignUserGroupPermissionComponent,
    AddUserToGroupComponent,
    NodeDetailsComponent,
    NodeUpdateComponent,
    AddNodeComponent,
    NodeEditComponent,
    NodeMaintenanceComponent,
    NodeConversionComponent,
    UserBulkManipulationComponent,
    UserBulkAssignPermissionComponent,
    UserBulkAddToGroupComponent,
    UserGroupBulkManipulationComponent,
    UserGroupBulkAssignPermissionComponent,
  ],
  imports: [
    CommonModule,
    CoreModule,
    AdminRoutingModule,
    MatTabsModule,
    MatCardModule,
    PasswordStrengthMeterModule.forRoot(),
  ],
})
export class AdminModule {}
