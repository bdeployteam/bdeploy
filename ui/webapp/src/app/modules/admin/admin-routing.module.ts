import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { CanDeactivateGuard } from '../shared/guards/can-deactivate.guard';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { AuditLogsBrowserComponent } from './components/audit-logs-browser/audit-logs-browser.component';
import { HiveAuditLogsBrowserComponent } from './components/hive-audit-logs-browser/hive-audit-logs-browser.component';
import { BackButtonGuard } from './components/hive-browser/back-button.guard';
import { HiveBrowserComponent } from './components/hive-browser/hive-browser.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { SettingsAuthComponent } from './components/settings-auth/settings-auth.component';
import { SettingsGeneralComponent } from './components/settings-general/settings-general.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';
import { UsersBrowserComponent } from './components/users-browser/users-browser.component';

const ADMIN_ROUTES: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'all'
  },
  {
    path: 'all',
    component: AdminShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'general', outlet: 'panel' },
      { path: 'general', component: SettingsGeneralComponent, canDeactivate: [CanDeactivateGuard], outlet: 'panel' },
      { path: 'authentication', component: SettingsAuthComponent, canDeactivate: [CanDeactivateGuard], outlet: 'panel' },
      { path: 'users', component: UsersBrowserComponent, canDeactivate: [CanDeactivateGuard], outlet: 'panel' },
      { path: 'hive', component: HiveBrowserComponent, canDeactivate: [BackButtonGuard], outlet: 'panel' },
      { path: 'hiveauditlogs', component: HiveAuditLogsBrowserComponent, outlet: 'panel' },
      { path: 'systemsoftware', component: UpdateBrowserComponent, outlet: 'panel' },
      { path: 'manualcleanup', component: MasterCleanupComponent, outlet: 'panel' },
      { path: 'metrics', component: MetricsOverviewComponent, outlet: 'panel' },
      { path: 'auditlogs', component: AuditLogsBrowserComponent, outlet: 'panel' },
    ]
  },
];

@NgModule({
  imports: [RouterModule.forChild(ADMIN_ROUTES)],
  exports: [RouterModule],
})
export class AdminRoutingModule { }
