import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { InstancesBrowserComponent as BrowserComponent } from './components/browser/browser.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';

const INSTANCES_ROUTES: Route[] = [
  { path: 'browser/:group', component: BrowserComponent },
  { path: 'dashboard/:group/:instance', component: DashboardComponent },
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
