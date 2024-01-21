import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { BdBHiveBrowserComponent } from '../../core/components/bd-bhive-browser/bd-bhive-browser.component';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { ServerCentralGuard } from '../../core/guards/server-central.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { ProductBulkComponent } from './components/product-bulk/product-bulk.component';
import { ProductDetailsComponent } from './components/product-details/product-details.component';
import { ManagedTransferComponent } from './components/product-sync/managed-transfer/managed-transfer.component';
import { ProductSyncComponent } from './components/product-sync/product-sync.component';
import { SelectManagedServerComponent } from './components/product-sync/select-managed-server/select-managed-server.component';
import { ProductTransferRepoComponent } from './components/product-transfer-repo/product-transfer-repo.component';
import { ProductUploadComponent } from './components/product-upload/product-upload.component';

const PPRODUCTS_ROUTES: Route[] = [
  {
    path: 'upload',
    component: ProductUploadComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'details/:key/:tag',
    component: ProductDetailsComponent,
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'details/:key/:tag/browse/:type',
    component: BdBHiveBrowserComponent,
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'sync',
    component: ProductSyncComponent,
    canActivate: [ScopedWriteGuard, ServerCentralGuard],
  },
  {
    path: 'sync/:target/select',
    component: SelectManagedServerComponent,
    canActivate: [ScopedWriteGuard, ServerCentralGuard],
  },
  {
    path: 'sync/:target/:server',
    component: ManagedTransferComponent,
    canActivate: [ScopedWriteGuard, ServerCentralGuard],
  },
  {
    path: 'bulk-manip',
    component: ProductBulkComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'transfer',
    component: ProductTransferRepoComponent,
    canActivate: [ScopedWriteGuard],
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(PPRODUCTS_ROUTES))],
  exports: [RouterModule],
})
export class ProductsRoutingModule {}
