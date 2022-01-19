import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AddGlobalAttributeComponent } from './components/add-global-attribute/add-global-attribute.component';
import { AddLdapServerComponent } from './components/add-ldap-server/add-ldap-server.component';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { AuthTestComponent } from './components/auth-test/auth-test.component';
import { BhiveAuditComponent } from './components/bhive-details/bhive-audit/bhive-audit.component';
import { BHiveBrowserComponent } from './components/bhive-details/bhive-browser/bhive-browser.component';
import { ManifestDeleteActionComponent } from './components/bhive-details/bhive-browser/manifest-delete-action/manifest-delete-action.component';
import { BhiveDetailsComponent } from './components/bhive-details/bhive-details.component';
import { CheckLdapServerComponent } from './components/check-ldap-server/check-ldap-server.component';
import { EditGlobalAttributeComponent } from './components/edit-global-attribute/edit-global-attribute.component';
import { EditLdapServerComponent } from './components/edit-ldap-server/edit-ldap-server.component';
import { LogConfigEditorComponent } from './components/log-config-editor/log-config-editor.component';
import { LogFileViewerComponent } from './components/log-file-viewer/log-file-viewer.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';
import { UserAdminDetailComponent } from './components/user-admin-detail/user-admin-detail.component';

@NgModule({
  declarations: [
    AddPluginComponent,
    UserAdminDetailComponent,
    BhiveDetailsComponent,
    BhiveAuditComponent,
    BHiveBrowserComponent,
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
  ],
  imports: [CommonModule, CoreModule, AdminRoutingModule],
})
export class AdminModule {}
