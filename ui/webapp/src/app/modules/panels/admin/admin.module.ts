import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AddPluginComponent } from './add-plugin/add-plugin.component';
import { AdminRoutingModule } from './admin-routing.module';

@NgModule({
  declarations: [AddPluginComponent],
  imports: [CommonModule, CoreModule, AdminRoutingModule],
})
export class AdminModule {}
