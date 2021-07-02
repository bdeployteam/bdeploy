import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { LabelsComponent } from './components/product-details/labels/labels.component';
import { PluginsComponent } from './components/product-details/plugins/plugins.component';
import { ProductDetailsComponent } from './components/product-details/product-details.component';
import { ApplicationComponent } from './components/product-details/templates/application/application.component';
import { InstanceComponent } from './components/product-details/templates/instance/instance.component';
import { ManagedTransferComponent } from './components/product-sync/managed-transfer/managed-transfer.component';
import { ProductSyncComponent } from './components/product-sync/product-sync.component';
import { SelectManagedServerComponent } from './components/product-sync/select-managed-server/select-managed-server.component';
import { ProductUploadComponent } from './components/product-upload/product-upload.component';
import { ProductsRoutingModule } from './products-routing.module';

@NgModule({
  declarations: [
    ProductUploadComponent,
    ProductDetailsComponent,
    LabelsComponent,
    ApplicationComponent,
    InstanceComponent,
    PluginsComponent,
    ProductSyncComponent,
    SelectManagedServerComponent,
    ManagedTransferComponent,
  ],
  imports: [CommonModule, CoreModule, ProductsRoutingModule],
})
export class ProductsModule {}
