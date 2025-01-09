import { Route } from '@angular/router';

import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { ServerCentralGuard } from '../../core/guards/server-central.guard';








export const PRODUCTS_PANEL_ROUTES: Route[] = [
  {
    path: 'upload',
    loadComponent: () => import('./components/product-upload/product-upload.component').then(m => m.ProductUploadComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'details/:key/:tag',
    loadComponent: () => import('./components/product-details/product-details.component').then(m => m.ProductDetailsComponent),
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'details/:key/:tag/browse/:type',
    loadComponent: () => import('../../core/components/bd-bhive-browser/bd-bhive-browser.component').then(m => m.BdBHiveBrowserComponent),
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'sync',
    loadComponent: () => import('./components/product-sync/product-sync.component').then(m => m.ProductSyncComponent),
    canActivate: [ScopedWriteGuard, ServerCentralGuard],
  },
  {
    path: 'sync/:target/select',
    loadComponent: () => import('./components/product-sync/select-managed-server/select-managed-server.component').then(m => m.SelectManagedServerComponent),
    canActivate: [ScopedWriteGuard, ServerCentralGuard],
  },
  {
    path: 'sync/:target/:server',
    loadComponent: () => import('./components/product-sync/managed-transfer/managed-transfer.component').then(m => m.ManagedTransferComponent),
    canActivate: [ScopedWriteGuard, ServerCentralGuard],
  },
  {
    path: 'bulk-manip',
    loadComponent: () => import('./components/product-bulk/product-bulk.component').then(m => m.ProductBulkComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'transfer',
    loadComponent: () => import('./components/product-transfer-repo/product-transfer-repo.component').then(m => m.ProductTransferRepoComponent),
    canActivate: [ScopedWriteGuard],
  },
];
