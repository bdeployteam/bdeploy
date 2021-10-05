import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { ServerCentralGuard } from '../../core/guards/server-central.guard';
import { ServerManagedGuard } from '../../core/guards/server-managed.guard';
import { LinkCentralComponent } from './components/link-central/link-central.component';
import { LinkManagedComponent } from './components/link-managed/link-managed.component';
import { ServerDetailsComponent } from './components/server-details/server-details.component';
import { ServerEditComponent } from './components/server-details/server-edit/server-edit.component';

const SERVERS_ROUTES: Route[] = [
  { path: 'details/:server', component: ServerDetailsComponent, canActivate: [ScopedAdminGuard] },
  { path: 'details/:server/edit', component: ServerEditComponent, canActivate: [ScopedAdminGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'link/central', component: LinkCentralComponent, canActivate: [ServerManagedGuard, ScopedAdminGuard] },
  { path: 'link/managed', component: LinkManagedComponent, canActivate: [ServerCentralGuard, ScopedAdminGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(SERVERS_ROUTES)],
  exports: [RouterModule],
})
export class ServersRoutingModule {}
