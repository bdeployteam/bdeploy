import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';
import { InstancesRoutingModule } from './instances-routing.module';

@NgModule({
  declarations: [AddInstanceComponent],
  imports: [CommonModule, CoreModule, InstancesRoutingModule],
})
export class InstancesModule {}
