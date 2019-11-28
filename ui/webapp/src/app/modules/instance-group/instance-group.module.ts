import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../core/core.module';
import { SharedModule } from '../shared/shared.module';
import { ClientAppsComponent } from './components/client-apps/client-apps.component';
import { InstanceGroupAddEditComponent } from './components/instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './components/instance-group-browser/instance-group-browser.component';
import { InstanceGroupCardComponent } from './components/instance-group-card/instance-group-card.component';
import { InstanceGroupDeleteDialogComponent } from './components/instance-group-delete-dialog/instance-group-delete-dialog.component';
import { InstanceGroupLogoComponent } from './components/instance-group-logo/instance-group-logo.component';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { ProductInfoCardComponent } from './components/product-info-card/product-info-card.component';
import { ProductListComponent } from './components/product-list/product-list.component';
import { ProductTagCardComponent } from './components/product-tag-card/product-tag-card.component';
import { ProductsComponent } from './components/products/products.component';
import { InstanceGroupRoutingModule } from './instance-group-routing.module';

@NgModule({
  declarations: [
    ProductsComponent,
    InstanceGroupLogoComponent,
    InstanceGroupBrowserComponent,
    InstanceGroupCardComponent,
    InstanceGroupAddEditComponent,
    InstanceGroupDeleteDialogComponent,
    InstanceGroupLogoComponent,
    ProductCardComponent,
    ProductListComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
    ClientAppsComponent,
  ],
  entryComponents: [
    InstanceGroupDeleteDialogComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    InstanceGroupRoutingModule
  ],
  exports: [
    InstanceGroupLogoComponent,
    InstanceGroupCardComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
  ]
})
export class InstanceGroupModule { }
