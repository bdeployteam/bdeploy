import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';



export const GROUPS_ROUTES: Route[] = [
  {
    path: 'browser',
    loadComponent: () => import('./components/groups-browser/groups-browser.component').then(m => m.GroupsBrowserComponent),
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'clients/:group',
    loadComponent: () => import('./components/client-applications/client-applications.component').then(m => m.ClientApplicationsComponent),
    canDeactivate: [DirtyDialogGuard],
  },
];
