import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CoreModule } from '../../core/core.module';
import { InstancesModule as PrimaryInstancesModule } from '../../primary/instances/instances.module';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { ProcessStatusComponent } from './components/process-status/process-status.component';
import { InstancesRoutingModule } from './instances-routing.module';
import { ProcessPortsComponent } from './components/process-ports/process-ports.component';
import { ProcessNativesComponent } from './components/process-natives/process-natives.component';
import { ProcessConsoleComponent } from './components/process-console/process-console.component';
import { BulkControlComponent } from './components/bulk-control/bulk-control.component';

@NgModule({
  declarations: [AddInstanceComponent, ProcessStatusComponent, NodeDetailsComponent, ProcessPortsComponent, ProcessNativesComponent, ProcessConsoleComponent, BulkControlComponent],
  imports: [CommonModule, CoreModule, InstancesRoutingModule, PrimaryInstancesModule, MatProgressSpinnerModule],
})
export class InstancesModule {}
