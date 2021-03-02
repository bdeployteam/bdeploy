import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ProductDetailsLabelsComponent } from './components/product-details-labels/product-details-labels.component';
import { ProductDetailsTemplatesAppComponent } from './components/product-details-templates-app/product-details-templates-app.component';
import { ProductDetailsTemplatesInstanceComponent } from './components/product-details-templates-instance/product-details-templates-instance.component';
import { ProductDetailsComponent } from './components/product-details/product-details.component';
import { ProductUploadComponent } from './components/product-upload/product-upload.component';
import { ProductsRoutingModule } from './products-routing.module';
import { ProductDetailsPluginsComponent } from './components/product-details-plugins/product-details-plugins.component';

@NgModule({
  declarations: [
    ProductUploadComponent,
    ProductDetailsComponent,
    ProductDetailsLabelsComponent,
    ProductDetailsTemplatesAppComponent,
    ProductDetailsTemplatesInstanceComponent,
    ProductDetailsPluginsComponent,
  ],
  imports: [CommonModule, CoreModule, ProductsRoutingModule],
})
export class ProductsModule {}
