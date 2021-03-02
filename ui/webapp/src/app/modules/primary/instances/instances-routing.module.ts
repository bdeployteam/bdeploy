import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { InstancesBrowserComponent } from './components/instances-browser/instances-browser.component';

const INSTANCES_ROUTES: Route[] = [{ path: 'browser/:group', component: InstancesBrowserComponent }];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
