import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';
import { BulkControlComponent } from './components/bulk-control/bulk-control.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { ProcessConsoleComponent } from './components/process-console/process-console.component';
import { ProcessNativesComponent } from './components/process-natives/process-natives.component';
import { ProcessPortsComponent } from './components/process-ports/process-ports.component';
import { ProcessStatusComponent } from './components/process-status/process-status.component';

const INSTANCES_ROUTES: Route[] = [
  { path: 'add', component: AddInstanceComponent, canActivate: [ScopedWriteGuard] },
  { path: 'bulk', component: BulkControlComponent, canActivate: [ScopedWriteGuard] },
  { path: 'node/:node', component: NodeDetailsComponent, canActivate: [ScopedReadGuard] },
  { path: 'process/:process', component: ProcessStatusComponent, canActivate: [ScopedReadGuard] },
  { path: 'process/:process/ports', component: ProcessPortsComponent, canActivate: [ScopedReadGuard] },
  { path: 'process/:process/natives', component: ProcessNativesComponent, canActivate: [ScopedReadGuard], data: { max: true } },
  { path: 'process/:process/console', component: ProcessConsoleComponent, canActivate: [ScopedReadGuard], data: { max: true } },
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
