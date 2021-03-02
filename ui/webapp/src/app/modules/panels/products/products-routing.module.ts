import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { PanelScopedWriteGuard } from '../../core/guards/panel-scoped-write.guard';
import { ProductDetailsLabelsComponent } from './components/product-details-labels/product-details-labels.component';
import { ProductDetailsPluginsComponent } from './components/product-details-plugins/product-details-plugins.component';
import { ProductDetailsTemplatesAppComponent } from './components/product-details-templates-app/product-details-templates-app.component';
import { ProductDetailsTemplatesInstanceComponent } from './components/product-details-templates-instance/product-details-templates-instance.component';
import { ProductDetailsComponent } from './components/product-details/product-details.component';
import { ProductUploadComponent } from './components/product-upload/product-upload.component';

const PPRODUCTS_ROUTES: Route[] = [
  { path: 'upload', component: ProductUploadComponent, canActivate: [PanelScopedWriteGuard] },
  { path: 'details/:key/:tag', component: ProductDetailsComponent },
  { path: 'details/:key/:tag/labels', component: ProductDetailsLabelsComponent },
  { path: 'details/:key/:tag/templates/application', component: ProductDetailsTemplatesAppComponent },
  { path: 'details/:key/:tag/templates/instance', component: ProductDetailsTemplatesInstanceComponent },
  { path: 'details/:key/:tag/plugins', component: ProductDetailsPluginsComponent },
];

@NgModule({
  imports: [RouterModule.forChild(PPRODUCTS_ROUTES)],
  exports: [RouterModule],
})
export class ProductsRoutingModule {}
