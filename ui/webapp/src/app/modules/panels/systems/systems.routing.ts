import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';





export const SYSTEMS_PANEL_ROUTES: Route[] = [
  {
    path: 'add',
    loadComponent: () => import('./components/add-system/add-system.component').then(m => m.AddSystemComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'details/:skey',
    loadComponent: () => import('./components/system-details/system-details.component').then(m => m.SystemDetailsComponent),
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'details/:skey/edit',
    loadComponent: () => import('./components/system-details/system-edit/system-edit.component').then(m => m.SystemEditComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'details/:skey/variables',
    loadComponent: () => import('./components/system-details/system-variables/system-variables.component').then(m => m.SystemVariablesComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
];
