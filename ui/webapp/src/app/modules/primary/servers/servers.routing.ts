import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';


export const SERVERS_ROUTES: Route[] = [
  {
    path: 'browser/:group',
    loadComponent: () => import('./components/servers-browser/servers-browser.component').then(m => m.ServersBrowserComponent),
    canActivate: [ScopedAdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];
