import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';



export const REPOS_ROUTES: Route[] = [
  {
    path: 'browser',
    loadComponent: () => import('./components/repositories-browser/repositories-browser.component').then(m => m.RepositoriesBrowserComponent),
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'repository/:repository',
    loadComponent: () => import('./components/repository/repository.component').then(m => m.RepositoryComponent),
    canDeactivate: [DirtyDialogGuard],
  },
];
