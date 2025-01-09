import { Route } from '@angular/router';
import { CanDeactivateGuard } from '../../core/guards/can-deactivate.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';












export const ADMIN_ROUTES: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: '/admin/all/(admin:general)',
  },
  {
    path: 'all',
    loadComponent: () => import('./components/admin-shell/admin-shell.component').then(m => m.AdminShellComponent),
    children: [
      {
        path: 'general',
        loadComponent: () => import('./components/settings-general/settings-general.component').then(m => m.SettingsGeneralComponent),
        canDeactivate: [DirtyDialogGuard],
        outlet: 'admin',
      },
      {
        path: 'users',
        loadComponent: () => import('./components/users-browser/users-browser.component').then(m => m.UsersBrowserComponent),
        canDeactivate: [CanDeactivateGuard],
        outlet: 'admin',
      },
      {
        path: 'user-groups',
        loadComponent: () => import('./components/user-groups-browser/user-groups-browser.component').then(m => m.UserGroupsBrowserComponent),
        canDeactivate: [CanDeactivateGuard],
        outlet: 'admin',
      },
      {
        path: 'nodes',
        loadComponent: () => import('./components/nodes/nodes.component').then(m => m.NodesComponent),
        outlet: 'admin',
      },
      { path: 'hive', loadComponent: () => import('./components/bhive/bhive.component').then(m => m.BHiveComponent), outlet: 'admin' },
      {
        path: 'systemsoftware',
        loadComponent: () => import('./components/update-browser/update-browser.component').then(m => m.UpdateBrowserComponent),
        outlet: 'admin',
      },
      {
        path: 'manualcleanup',
        loadComponent: () => import('./components/master-cleanup/master-cleanup.component').then(m => m.MasterCleanupComponent),
        outlet: 'admin',
      },
      {
        path: 'manualjobs',
        loadComponent: () => import('./components/manual-jobs/manual-jobs.component').then(m => m.ManualJobsComponent),
        outlet: 'admin',
      },
      { path: 'metrics', loadComponent: () => import('./components/metrics-overview/metrics-overview.component').then(m => m.MetricsOverviewComponent), outlet: 'admin' },
      {
        path: 'logFiles',
        loadComponent: () => import('./components/log-files-browser/log-files-browser.component').then(m => m.LogFilesBrowserComponent),
        outlet: 'admin',
      },
    ],
  },
];
