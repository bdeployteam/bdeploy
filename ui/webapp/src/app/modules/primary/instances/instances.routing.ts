import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';







export const INSTANCES_ROUTES: Route[] = [
  {
    path: 'browser/:group',
    loadComponent: () => import('./components/browser/browser.component').then(m => m.InstancesBrowserComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'dashboard/:group/:instance',
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'configuration/:group/:instance',
    loadComponent: () => import('./components/configuration/configuration.component').then(m => m.ConfigurationComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'history/:group/:instance',
    loadComponent: () => import('./components/history/history.component').then(m => m.HistoryComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'data-files/:group/:instance/:path',
    loadComponent: () => import('./components/files-display/files-display.component').then(m => m.FilesDisplayComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { isDataFiles: true },
  },
  {
    path: 'log-files/:group/:instance/:path',
    loadComponent: () => import('./components/files-display/files-display.component').then(m => m.FilesDisplayComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { isDataFiles: false },
  },
  {
    path: 'system-template/:group',
    loadComponent: () => import('./components/system-template/system-template.component').then(m => m.SystemTemplateComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];
