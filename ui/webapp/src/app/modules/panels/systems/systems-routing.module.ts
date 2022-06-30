import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { AddSystemComponent } from './components/add-system/add-system.component';
import { SystemDetailsComponent } from './components/system-details/system-details.component';
import { SystemEditComponent } from './components/system-details/system-edit/system-edit.component';
import { SystemVariablesComponent } from './components/system-details/system-variables/system-variables.component';

const SYSTEMS_ROUTES: Route[] = [
  {
    path: 'add',
    component: AddSystemComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'details/:skey',
    component: SystemDetailsComponent,
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'details/:skey/edit',
    component: SystemEditComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'details/:skey/variables',
    component: SystemVariablesComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(SYSTEMS_ROUTES))],
  exports: [RouterModule],
})
export class SystemsRoutingModule {}
