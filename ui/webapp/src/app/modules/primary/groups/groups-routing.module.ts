import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ClientApplicationsComponent } from './components/client-applications/client-applications.component';
import { GroupsBrowserComponent } from './components/groups-browser/groups-browser.component';

const GROUPS_ROUTES: Route[] = [
  { path: 'browser', component: GroupsBrowserComponent, canDeactivate: [DirtyDialogGuard] },
  { path: 'clients/:group', component: ClientApplicationsComponent, canDeactivate: [DirtyDialogGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(GROUPS_ROUTES)],
  exports: [RouterModule],
})
export class GroupsRoutingModule {}
