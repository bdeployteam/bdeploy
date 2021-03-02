import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ProductsBrowserComponent } from './components/products-browser/products-browser.component';
import { ProductsRoutingModule } from './products-routing.module';

@NgModule({
  declarations: [ProductsBrowserComponent],
  imports: [CommonModule, CoreModule, ProductsRoutingModule],
})
export class ProductsModule {}
