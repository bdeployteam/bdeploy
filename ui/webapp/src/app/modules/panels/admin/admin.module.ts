import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { BhiveAuditComponent } from './components/bhive-details/bhive-audit/bhive-audit.component';
import { BHiveBrowserComponent } from './components/bhive-details/bhive-browser/bhive-browser.component';
import { ManifestDeleteActionComponent } from './components/bhive-details/bhive-browser/manifest-delete-action/manifest-delete-action.component';
import { BhiveDetailsComponent } from './components/bhive-details/bhive-details.component';
import { UserAdminDetailComponent } from './components/user-admin-detail/user-admin-detail.component';
import { LogFileViewerComponent } from './components/log-file-viewer/log-file-viewer.component';
import { LogConfigEditorComponent } from './components/log-config-editor/log-config-editor.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';

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
  ],
  imports: [CommonModule, CoreModule, AdminRoutingModule],
})
export class AdminModule {}
