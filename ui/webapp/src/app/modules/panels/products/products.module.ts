import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatStepperModule } from '@angular/material/stepper';
import { CoreModule } from '../../core/core.module';
import { ProductBulkComponent } from './components/product-bulk/product-bulk.component';
import { ProductDetailsComponent } from './components/product-details/product-details.component';
import { ManagedTransferComponent } from './components/product-sync/managed-transfer/managed-transfer.component';
import { ProductSyncComponent } from './components/product-sync/product-sync.component';
import { SelectManagedServerComponent } from './components/product-sync/select-managed-server/select-managed-server.component';
import { ProductTransferRepoComponent } from './components/product-transfer-repo/product-transfer-repo.component';
import { ProductUploadComponent } from './components/product-upload/product-upload.component';
import { ProductsRoutingModule } from './products-routing.module';

@NgModule({
  declarations: [
    ProductUploadComponent,
    ProductDetailsComponent,
    ProductSyncComponent,
    SelectManagedServerComponent,
    ManagedTransferComponent,
    ProductBulkComponent,
    ProductTransferRepoComponent,
  ],
  imports: [CommonModule, CoreModule, ProductsRoutingModule, MatStepperModule],
})
export class ProductsModule {}
