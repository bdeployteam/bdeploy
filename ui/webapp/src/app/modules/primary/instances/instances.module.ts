import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { InstanceProductVersionComponent } from './components/instance-product-version/instance-product-version.component';
import { InstancesBrowserComponent } from './components/instances-browser/instances-browser.component';
import { InstancesRoutingModule } from './instances-routing.module';

@NgModule({
  declarations: [InstancesBrowserComponent, InstanceProductVersionComponent],
  imports: [CommonModule, CoreModule, InstancesRoutingModule],
})
export class InstancesModule {}
