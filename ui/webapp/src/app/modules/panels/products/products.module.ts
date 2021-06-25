import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { LabelsComponent } from './components/product-details/labels/labels.component';
import { PluginsComponent } from './components/product-details/plugins/plugins.component';
import { ProductDetailsComponent } from './components/product-details/product-details.component';
import { ApplicationComponent } from './components/product-details/templates/application/application.component';
import { InstanceComponent } from './components/product-details/templates/instance/instance.component';
import { ProductUploadComponent } from './components/product-upload/product-upload.component';
import { ProductsRoutingModule } from './products-routing.module';
import { ProductSyncComponent } from './components/product-sync/product-sync.component';

@NgModule({
  declarations: [ProductUploadComponent, ProductDetailsComponent, LabelsComponent, ApplicationComponent, InstanceComponent, PluginsComponent, ProductSyncComponent],
  imports: [CommonModule, CoreModule, ProductsRoutingModule],
})
export class ProductsModule {}
