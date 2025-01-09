import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';


export const SYSTEMS_ROUTES: Route[] = [
  {
    path: 'browser/:group',
    loadComponent: () => import('./components/system-browser/system-browser.component').then(m => m.SystemBrowserComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];
