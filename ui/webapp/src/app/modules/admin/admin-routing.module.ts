import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AuthGuard } from '../shared/guards/authentication.guard';
import { CanDeactivateGuard } from '../shared/guards/can-deactivate.guard';
import { BackButtonGuard } from './components/hive-browser/back-button.guard';
import { HiveBrowserComponent } from './components/hive-browser/hive-browser.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';

const ADMIN_ROUTES: Route[] = [
  {
    path: 'hive/browser',
    component: HiveBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [BackButtonGuard],
    data: { title: 'Hive Browser', header: 'Hive Browser' }
  },
  {
    path: 'systemsoftware',
    component: UpdateBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'System Software', header: 'System Software' }
  },
  {
    path: 'manualcleanup',
    component: MasterCleanupComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Manual Cleanup', header: 'Manual Cleanup' }
  },
  {
    path: 'metrics',
    component: MetricsOverviewComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'System Metrics', header: 'System Metrics' }
  },
];

@NgModule({
  imports: [RouterModule.forChild(ADMIN_ROUTES)],
  exports: [RouterModule],
})
export class AdminRoutingModule { }
