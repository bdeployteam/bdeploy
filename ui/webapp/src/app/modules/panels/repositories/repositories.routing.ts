import { Route } from '@angular/router';

import { AdminGuard } from '../../core/guards/admin.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';








export const REPOS_PANEL_ROUTES: Route[] = [
  {
    path: 'add',
    loadComponent: () => import('./components/add-repository/add-repository.component').then(m => m.AddRepositoryComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings',
    loadComponent: () => import('./components/settings/settings.component').then(m => m.SettingsComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/edit',
    loadComponent: () => import('./components/settings/edit/edit.component').then(m => m.EditComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings/permissions',
    loadComponent: () => import('./components/settings/permissions/permissions.component').then(m => m.PermissionsComponent),
    canActivate: [ScopedAdminGuard],
    data: { max: true },
  },
  {
    path: 'upload',
    loadComponent: () => import('./components/software-upload/software-upload.component').then(m => m.SoftwareUploadComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'details/:key/:tag',
    loadComponent: () => import('./components/software-details/software-details.component').then(m => m.SoftwareDetailsComponent),
  },
  {
    path: 'details/:key/:tag/browse/:type',
    loadComponent: () => import('../../core/components/bd-bhive-browser/bd-bhive-browser.component').then(m => m.BdBHiveBrowserComponent),
    data: { max: true },
  },
  {
    path: 'bulk-manip',
    loadComponent: () => import('./components/software-details-bulk/software-details-bulk.component').then(m => m.SoftwareDetailsBulkComponent),
  },
];
