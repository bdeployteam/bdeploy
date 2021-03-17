import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ServersBrowserComponent } from './components/servers-browser/servers-browser.component';

const SERVERS_ROUTES: Route[] = [{ path: 'browser/:group', component: ServersBrowserComponent }];

@NgModule({
  imports: [RouterModule.forChild(SERVERS_ROUTES)],
  exports: [RouterModule],
})
export class ServersRoutingModule {}
