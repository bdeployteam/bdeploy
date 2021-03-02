import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { PanelScopedWriteGuard } from '../../core/guards/panel-scoped-write.guard';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';

const INSTANCES_ROUTES: Route[] = [
  { path: 'add', component: AddInstanceComponent, canActivate: [PanelScopedWriteGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
