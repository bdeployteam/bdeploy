import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ProductsBrowserComponent } from './components/products-browser/products-browser.component';

const PRODUCTS_ROUTES: Route[] = [{ path: 'browser/:group', component: ProductsBrowserComponent }];

@NgModule({
  imports: [RouterModule.forChild(PRODUCTS_ROUTES)],
  exports: [RouterModule],
})
export class ProductsRoutingModule {}
