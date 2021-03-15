import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CoreModule } from '../../core/core.module';
import { SharedModule } from '../../legacy/shared/shared.module';
import { InstanceGroupCardComponent } from './components/instance-group-card/instance-group-card.component';
import { InstanceGroupLogoComponent } from './components/instance-group-logo/instance-group-logo.component';
import { InstanceGroupTitleComponent } from './components/instance-group-title/instance-group-title.component';
import { ManagedServerUpdateComponent } from './components/managed-server-update/managed-server-update.component';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { ProductInfoCardComponent } from './components/product-info-card/product-info-card.component';
import { ProductListComponent } from './components/product-list/product-list.component';
import { ProductTagCardComponent } from './components/product-tag-card/product-tag-card.component';
import { UserEditComponent } from './components/user-edit/user-edit.component';
import { UserPasswordComponent } from './components/user-password/user-password.component';

@NgModule({
  declarations: [
    ManagedServerUpdateComponent,
    InstanceGroupCardComponent,
    InstanceGroupLogoComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
    InstanceGroupTitleComponent,
    ProductCardComponent,
    ProductListComponent,
    UserEditComponent,
    UserPasswordComponent,
  ],
  imports: [CommonModule, CoreModule, SharedModule, RouterModule],
  exports: [
    ManagedServerUpdateComponent,
    InstanceGroupCardComponent,
    InstanceGroupLogoComponent,
    InstanceGroupTitleComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
    ProductCardComponent,
    ProductListComponent,
  ],
})
export class CoreLegacyModule {}
