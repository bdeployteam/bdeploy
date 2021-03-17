import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ServerCentralGuard } from '../../core/guards/server-central.guard';
import { ServerManagedGuard } from '../../core/guards/server-managed.guard';
import { LinkCentralComponent } from './components/link-central/link-central.component';
import { LinkManagedComponent } from './components/link-managed/link-managed.component';
import { ServerDetailsComponent } from './components/server-details/server-details.component';

const SERVERS_ROUTES: Route[] = [
  { path: 'details/:server', component: ServerDetailsComponent, canActivate: [ServerCentralGuard] },
  { path: 'link/central', component: LinkCentralComponent, canActivate: [ServerManagedGuard] },
  { path: 'link/managed', component: LinkManagedComponent, canActivate: [ServerCentralGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(SERVERS_ROUTES)],
  exports: [RouterModule],
})
export class ServersRoutingModule {}
