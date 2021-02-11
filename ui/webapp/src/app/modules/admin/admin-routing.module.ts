import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { CanDeactivateGuard } from '../core/guards/can-deactivate.guard';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { AuditLogsBrowserComponent } from './components/audit-logs-browser/audit-logs-browser.component';
import { HiveAuditLogsBrowserComponent } from './components/hive-audit-logs-browser/hive-audit-logs-browser.component';
import { HiveBrowserComponent } from './components/hive-browser/hive-browser.component';
import { LogFilesBrowserComponent } from './components/log-files-browser/log-files-browser.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { PluginsBrowserComponent } from './components/plugins-browser/plugins-browser.component';
import { SettingsAuthComponent } from './components/settings-auth/settings-auth.component';
import { SettingsGeneralComponent } from './components/settings-general/settings-general.component';
import { SettingsInstanceGroupComponent } from './components/settings-instance-group/settings-instance-group.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';
import { UsersBrowserComponent } from './components/users-browser/users-browser.component';

const ADMIN_ROUTES: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'all',
  },
  {
    path: 'all',
    component: AdminShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'general', outlet: 'admin' },
      { path: 'general', component: SettingsGeneralComponent, canDeactivate: [CanDeactivateGuard], outlet: 'admin' },
      { path: 'plugins', component: PluginsBrowserComponent, outlet: 'admin' },
      {
        path: 'instancegroup',
        component: SettingsInstanceGroupComponent,
        canDeactivate: [CanDeactivateGuard],
        outlet: 'admin',
      },
      {
        path: 'authentication',
        component: SettingsAuthComponent,
        canDeactivate: [CanDeactivateGuard],
        outlet: 'admin',
      },
      { path: 'users', component: UsersBrowserComponent, canDeactivate: [CanDeactivateGuard], outlet: 'admin' },
      { path: 'hive', component: HiveBrowserComponent, outlet: 'admin' },
      { path: 'hiveauditlogs', component: HiveAuditLogsBrowserComponent, outlet: 'admin' },
      { path: 'systemsoftware', component: UpdateBrowserComponent, outlet: 'admin' },
      { path: 'manualcleanup', component: MasterCleanupComponent, outlet: 'admin' },
      { path: 'metrics', component: MetricsOverviewComponent, outlet: 'admin' },
      { path: 'auditlogs', component: AuditLogsBrowserComponent, outlet: 'admin' },
      { path: 'logFiles', component: LogFilesBrowserComponent, outlet: 'admin' },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(ADMIN_ROUTES)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
