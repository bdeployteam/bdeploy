import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { ProductsBrowserComponent } from './components/products-browser/products-browser.component';

const PRODUCTS_ROUTES: Route[] = [
  {
    path: 'browser/:group',
    component: ProductsBrowserComponent,
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(PRODUCTS_ROUTES))],
  exports: [RouterModule],
})
export class ProductsRoutingModule {}
