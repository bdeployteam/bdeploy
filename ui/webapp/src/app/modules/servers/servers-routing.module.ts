import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ProductSyncComponent } from 'src/app/modules/servers/components/product-sync/product-sync.component';
import { AuthGuard } from '../shared/guards/authentication.guard';
import { CanDeactivateGuard } from '../shared/guards/can-deactivate.guard';
import { AttachCentralComponent } from './components/attach-central/attach-central.component';
import { AttachManagedComponent } from './components/attach-managed/attach-managed.component';
import { ManagedServersComponent } from './components/managed-servers/managed-servers.component';

const SERVERS_ROUTES: Route[] = [
  {
    path: 'attach/central',
    component: AttachCentralComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Attach to Central Server', header: 'Attach to Central Server' },
  },
  {
    path: 'attach/managed/:group',
    component: AttachManagedComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Attach Managed Server', header: 'Attach Managed Server' },
  },
  {
    path: 'browser/:group',
    component: ManagedServersComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Managed Servers', header: 'Managed Servers' },
  },
  {
    path: 'product-sync/:group',
    component: ProductSyncComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Transfer Product Versions (${params["group"]})', header: 'Transfer Product Versions' },
  },
];

@NgModule({
  imports: [RouterModule.forChild(SERVERS_ROUTES)],
  exports: [RouterModule],
})
export class ServersRoutingModule {}
