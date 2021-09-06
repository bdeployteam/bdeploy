import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AddPluginComponent } from './components/add-plugin/add-plugin.component';
import { UserAdminDetailComponent } from './components/user-admin-detail/user-admin-detail.component';

@NgModule({
  declarations: [AddPluginComponent, UserAdminDetailComponent],
  imports: [CommonModule, CoreModule, AdminRoutingModule],
})
export class AdminModule {}
