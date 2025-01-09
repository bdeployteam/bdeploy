import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';


export const PRODUCTS_ROUTES: Route[] = [
  {
    path: 'browser/:group',
    loadComponent: () => import('./components/products-browser/products-browser.component').then(m => m.ProductsBrowserComponent),
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];
