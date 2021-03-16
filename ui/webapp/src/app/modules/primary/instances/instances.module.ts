import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { InstanceProductVersionComponent } from './components/instances-browser/instance-product-version/instance-product-version.component';
import { InstancesBrowserComponent } from './components/instances-browser/instances-browser.component';
import { InstancesRoutingModule } from './instances-routing.module';
import { InstanceBannerHintComponent } from './components/instances-browser/instance-banner-hint/instance-banner-hint.component';

@NgModule({
  declarations: [InstancesBrowserComponent, InstanceProductVersionComponent, InstanceBannerHintComponent],
  imports: [CommonModule, CoreModule, InstancesRoutingModule],
})
export class InstancesModule {}
