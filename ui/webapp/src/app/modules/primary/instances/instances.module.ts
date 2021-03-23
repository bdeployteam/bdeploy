import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { InstancesBrowserComponent } from './components/browser/browser.component';
import { InstanceBannerHintComponent } from './components/browser/instance-banner-hint/instance-banner-hint.component';
import { InstanceProductVersionComponent } from './components/browser/instance-product-version/instance-product-version.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { InstancesRoutingModule } from './instances-routing.module';

@NgModule({
  declarations: [InstancesBrowserComponent, InstanceProductVersionComponent, InstanceBannerHintComponent, DashboardComponent],
  imports: [CommonModule, CoreModule, InstancesRoutingModule],
})
export class InstancesModule {}
