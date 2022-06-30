import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { SystemBrowserComponent } from './components/system-browser/system-browser.component';

const SYSTEMS_ROUTES: Route[] = [
  {
    path: 'browser/:group',
    component: SystemBrowserComponent,
    canActivate: [ScopedReadGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(SYSTEMS_ROUTES))],
  exports: [RouterModule],
})
export class SystemsRoutingModule {}
