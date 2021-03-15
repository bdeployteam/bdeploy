import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ProductService } from '../../legacy/shared/services/product.service';
import { SharedModule } from '../../legacy/shared/shared.module';
import { CoreLegacyModule } from '../core/core-legacy.module';
import { ClientAppsComponent } from './components/client-apps/client-apps.component';
import { InstanceGroupAddEditComponent } from './components/instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './components/instance-group-browser/instance-group-browser.component';
import { InstanceGroupDeleteDialogComponent } from './components/instance-group-delete-dialog/instance-group-delete-dialog.component';
import { InstanceGroupPermissionsComponent } from './components/instance-group-permissions/instance-group-permissions.component';
import { ProductsCopyComponent } from './components/products-copy/products-copy.component';
import { ProductsComponent } from './components/products/products.component';
import { InstanceGroupRoutingModule } from './instance-group-routing.module';
import { InstanceGroupService } from './services/instance-group.service';

@NgModule({
  declarations: [
    ProductsComponent,
    InstanceGroupBrowserComponent,
    InstanceGroupAddEditComponent,
    InstanceGroupDeleteDialogComponent,
    ClientAppsComponent,
    InstanceGroupPermissionsComponent,
    ProductsCopyComponent,
  ],
  providers: [ProductService, { provide: 'ProductBasePath', useValue: InstanceGroupService.BASEPATH }],
  imports: [CommonModule, SharedModule, CoreModule, CoreLegacyModule, InstanceGroupRoutingModule],
})
export class InstanceGroupModule {}
