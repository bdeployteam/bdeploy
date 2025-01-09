import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { ServerCentralGuard } from '../../core/guards/server-central.guard';
import { ServerManagedGuard } from '../../core/guards/server-managed.guard';






export const SERVERS_PANEL_ROUTES: Route[] = [
  {
    path: 'details/:server',
    loadComponent: () => import('./components/server-details/server-details.component').then(m => m.ServerDetailsComponent),
    canActivate: [ScopedAdminGuard],
  },
  {
    path: 'details/:server/nodes',
    loadComponent: () => import('./components/server-nodes/server-nodes.component').then(m => m.ServerNodesComponent),
    canActivate: [ScopedAdminGuard],
    data: { max: true },
  },
  {
    path: 'details/:server/edit',
    loadComponent: () => import('./components/server-details/server-edit/server-edit.component').then(m => m.ServerEditComponent),
    canActivate: [ScopedAdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'link/central',
    loadComponent: () => import('./components/link-central/link-central.component').then(m => m.LinkCentralComponent),
    canActivate: [ServerManagedGuard, ScopedAdminGuard],
  },
  {
    path: 'link/managed',
    loadComponent: () => import('./components/link-managed/link-managed.component').then(m => m.LinkManagedComponent),
    canActivate: [ServerCentralGuard, ScopedAdminGuard],
  },
];
