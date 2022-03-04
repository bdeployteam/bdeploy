import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { CoreModule } from '../../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AddGlobalAttributeComponent } from './components/add-global-attribute/add-global-attribute.component';
import { AddLdapServerComponent } from './components/add-ldap-server/add-ldap-server.component';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { AddUserComponent } from './components/add-user/add-user.component';
import { AssignPermissionComponent } from './components/assign-permission/assign-permission.component';
import { AuthTestComponent } from './components/auth-test/auth-test.component';
import { BHiveBrowserComponent } from './components/bhive-details/bhive-browser/bhive-browser.component';
import { ManifestDeleteActionComponent } from './components/bhive-details/bhive-browser/manifest-delete-action/manifest-delete-action.component';
import { BhiveDetailsComponent } from './components/bhive-details/bhive-details.component';
import { BhiveLogBrowserComponent } from './components/bhive-details/bhive-log-browser/bhive-log-browser.component';
import { BhiveLogViewerComponent } from './components/bhive-details/bhive-log-viewer/bhive-log-viewer.component';
import { CheckLdapServerComponent } from './components/check-ldap-server/check-ldap-server.component';
import { EditGlobalAttributeComponent } from './components/edit-global-attribute/edit-global-attribute.component';
import { EditLdapServerComponent } from './components/edit-ldap-server/edit-ldap-server.component';
import { EditUserComponent } from './components/edit-user/edit-user.component';
import { LogConfigEditorComponent } from './components/log-config-editor/log-config-editor.component';
import { LogFileViewerComponent } from './components/log-file-viewer/log-file-viewer.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';
import { UserAdminDetailComponent } from './components/user-admin-detail/user-admin-detail.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { NodeUpdateComponent } from './components/node-details/node-update/node-update.component';
import { AddNodeComponent } from './components/add-node/add-node.component';
import { NodeEditComponent } from './components/node-details/node-edit/node-edit.component';

@NgModule({
  declarations: [
    AddPluginComponent,
    UserAdminDetailComponent,
    BhiveDetailsComponent,
    BHiveBrowserComponent,
    BhiveLogViewerComponent,
    BhiveLogBrowserComponent,
    ManifestDeleteActionComponent,
    LogFileViewerComponent,
    LogConfigEditorComponent,
    SoftwareUploadComponent,
    SoftwareDetailsComponent,
    AddLdapServerComponent,
    EditLdapServerComponent,
    CheckLdapServerComponent,
    AuthTestComponent,
    AddGlobalAttributeComponent,
    EditGlobalAttributeComponent,
    AddUserComponent,
    EditUserComponent,
    AssignPermissionComponent,
    NodeDetailsComponent,
    NodeUpdateComponent,
    AddNodeComponent,
    NodeEditComponent,
  ],
  imports: [CommonModule, CoreModule, AdminRoutingModule, MatTabsModule],
})
export class AdminModule {}
