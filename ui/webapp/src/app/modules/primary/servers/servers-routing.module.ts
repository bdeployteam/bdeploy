import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { setRouteId } from '../../core/utils/routeId-generator';
import { ServersBrowserComponent } from './components/servers-browser/servers-browser.component';

const SERVERS_ROUTES: Route[] = [
  {
    path: 'browser/:group',
    component: ServersBrowserComponent,
    canActivate: [ScopedAdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(SERVERS_ROUTES))],
  exports: [RouterModule],
})
export class ServersRoutingModule {}
