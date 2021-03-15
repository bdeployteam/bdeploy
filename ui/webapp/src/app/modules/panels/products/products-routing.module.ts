import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { PanelScopedWriteGuard } from '../../core/guards/panel-scoped-write.guard';
import { LabelsComponent } from './components/product-details/labels/labels.component';
import { PluginsComponent } from './components/product-details/plugins/plugins.component';
import { ProductDetailsComponent } from './components/product-details/product-details.component';
import { ApplicationComponent } from './components/product-details/templates/application/application.component';
import { InstanceComponent } from './components/product-details/templates/instance/instance.component';
import { ProductUploadComponent } from './components/product-upload/product-upload.component';

const PPRODUCTS_ROUTES: Route[] = [
  { path: 'upload', component: ProductUploadComponent, canActivate: [PanelScopedWriteGuard] },
  { path: 'details/:key/:tag', component: ProductDetailsComponent },
  { path: 'details/:key/:tag/labels', component: LabelsComponent },
  { path: 'details/:key/:tag/templates/application', component: ApplicationComponent },
  { path: 'details/:key/:tag/templates/instance', component: InstanceComponent },
  { path: 'details/:key/:tag/plugins', component: PluginsComponent },
];

@NgModule({
  imports: [RouterModule.forChild(PPRODUCTS_ROUTES)],
  exports: [RouterModule],
})
export class ProductsRoutingModule {}
