import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminGuard } from '../../core/guards/admin.guard';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { BhiveAuditComponent as BHiveAuditComponent } from './components/bhive-details/bhive-audit/bhive-audit.component';
import { BHiveBrowserComponent as BHiveBrowserComponent } from './components/bhive-details/bhive-browser/bhive-browser.component';
import { BhiveDetailsComponent as BHiveDetailsComponent } from './components/bhive-details/bhive-details.component';
import { LogConfigEditorComponent } from './components/log-config-editor/log-config-editor.component';
import { LogFileViewerComponent } from './components/log-file-viewer/log-file-viewer.component';
import { UserAdminDetailComponent } from './components/user-admin-detail/user-admin-detail.component';

const routes: Routes = [
  { path: 'add-plugin', component: AddPluginComponent, canActivate: [AdminGuard] },
  { path: 'user-detail/:user', component: UserAdminDetailComponent, canActivate: [AdminGuard] },
  { path: 'bhive/:bhive', component: BHiveDetailsComponent, canActivate: [AdminGuard] },
  { path: 'bhive/:bhive/audit', component: BHiveAuditComponent, canActivate: [AdminGuard], data: { max: true } },
  { path: 'bhive/:bhive/browse', component: BHiveBrowserComponent, canActivate: [AdminGuard], data: { max: true } },
  { path: 'logging/view/:node/:file', component: LogFileViewerComponent, canActivate: [AdminGuard], data: { max: true } },
  { path: 'logging/config', component: LogConfigEditorComponent, canActivate: [AdminGuard], data: { max: true } },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
