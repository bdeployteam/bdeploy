import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { ServersBrowserComponent } from './components/servers-browser/servers-browser.component';

const SERVERS_ROUTES: Route[] = [{ path: 'browser/:group', component: ServersBrowserComponent, canActivate: [ScopedAdminGuard] }];

@NgModule({
  imports: [RouterModule.forChild(SERVERS_ROUTES)],
  exports: [RouterModule],
})
export class ServersRoutingModule {}
