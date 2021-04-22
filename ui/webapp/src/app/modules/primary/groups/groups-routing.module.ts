import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ClientApplicationsComponent } from './components/client-applications/client-applications.component';
import { GroupsBrowserComponent } from './components/groups-browser/groups-browser.component';

const GROUPS_ROUTES: Route[] = [
  { path: 'browser', component: GroupsBrowserComponent },
  { path: 'clients/:group', component: ClientApplicationsComponent },
];

@NgModule({
  imports: [RouterModule.forChild(GROUPS_ROUTES)],
  exports: [RouterModule],
})
export class GroupsRoutingModule {}
