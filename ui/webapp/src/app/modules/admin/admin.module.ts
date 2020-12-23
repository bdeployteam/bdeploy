import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { CoreModule } from '../core/core.module';
import { SharedModule } from '../shared/shared.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { AuditLogComponent } from './components/audit-log/audit-log.component';
import { AuditLogsBrowserComponent } from './components/audit-logs-browser/audit-logs-browser.component';
import { HiveAuditLogsBrowserComponent } from './components/hive-audit-logs-browser/hive-audit-logs-browser.component';
import { HiveBrowserComponent } from './components/hive-browser/hive-browser.component';
import { HiveComponent } from './components/hive/hive.component';
import { LogFilesBrowserComponent } from './components/log-files-browser/log-files-browser.component';
import { MasterCleanupGroupComponent } from './components/master-cleanup-group/master-cleanup-group.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { PluginsBrowserComponent } from './components/plugins-browser/plugins-browser.component';
import { SettingsAuthLdapServerComponent } from './components/settings-auth-ldap-server/settings-auth-ldap-server.component';
import { SettingsAuthTestUserComponent } from './components/settings-auth-test-user/settings-auth-test-user.component';
import { SettingsAuthComponent } from './components/settings-auth/settings-auth.component';
import { SettingsGeneralComponent } from './components/settings-general/settings-general.component';
import { SettingsInstanceGroupComponent } from './components/settings-instance-group/settings-instance-group.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';
import { UpdateCardComponent } from './components/update-card/update-card.component';
import { UpdateDialogComponent } from './components/update-dialog/update-dialog.component';
import { UserGlobalPermissionsComponent } from './components/user-global-permissions/user-global-permissions.component';
import { UsersBrowserComponent } from './components/users-browser/users-browser.component';

@NgModule({
  declarations: [
    AuditLogComponent,
    AuditLogsBrowserComponent,
    HiveComponent,
    HiveBrowserComponent,
    HiveAuditLogsBrowserComponent,
    LogFilesBrowserComponent,
    UpdateBrowserComponent,
    UpdateCardComponent,
    UpdateDialogComponent,
    MasterCleanupComponent,
    MetricsOverviewComponent,
    AdminShellComponent,
    SettingsAuthComponent,
    SettingsAuthLdapServerComponent,
    SettingsAuthTestUserComponent,
    SettingsGeneralComponent,
    SettingsInstanceGroupComponent,
    UsersBrowserComponent,
    UserGlobalPermissionsComponent,
    MasterCleanupGroupComponent,
    PluginsBrowserComponent,
  ],
  entryComponents: [
    UpdateDialogComponent,
    SettingsAuthLdapServerComponent,
    SettingsAuthTestUserComponent,
    UserGlobalPermissionsComponent,
  ],
  imports: [CommonModule, CoreModule, SharedModule, AdminRoutingModule, NgxChartsModule],
})
export class AdminModule {}
