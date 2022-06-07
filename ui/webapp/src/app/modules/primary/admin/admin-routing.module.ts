import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { CanDeactivateGuard } from '../../core/guards/can-deactivate.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { BHiveComponent } from './components/bhive/bhive.component';
import { LogFilesBrowserComponent } from './components/log-files-browser/log-files-browser.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { NodesComponent } from './components/nodes/nodes.component';
import { SettingsGeneralComponent } from './components/settings-general/settings-general.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';
import { UsersBrowserComponent } from './components/users-browser/users-browser.component';

const ADMIN_ROUTES: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: '/admin/all/(admin:general)',
  },
  {
    path: 'all',
    component: AdminShellComponent,
    children: [
      {
        path: 'general',
        component: SettingsGeneralComponent,
        canDeactivate: [DirtyDialogGuard],
        outlet: 'admin',
      },
      {
        path: 'users',
        component: UsersBrowserComponent,
        canDeactivate: [CanDeactivateGuard],
        outlet: 'admin',
      },
      {
        path: 'nodes',
        component: NodesComponent,
        outlet: 'admin',
      },
      { path: 'hive', component: BHiveComponent, outlet: 'admin' },
      {
        path: 'systemsoftware',
        component: UpdateBrowserComponent,
        outlet: 'admin',
      },
      {
        path: 'manualcleanup',
        component: MasterCleanupComponent,
        outlet: 'admin',
      },
      { path: 'metrics', component: MetricsOverviewComponent, outlet: 'admin' },
      {
        path: 'logFiles',
        component: LogFilesBrowserComponent,
        outlet: 'admin',
      },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(ADMIN_ROUTES))],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
