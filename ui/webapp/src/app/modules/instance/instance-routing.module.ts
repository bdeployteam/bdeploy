import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AuthGuard } from '../shared/guards/authentication.guard';
import { CanDeactivateGuard } from '../shared/guards/can-deactivate.guard';
import { DataFilesBrowserComponent } from './components/data-files-browser/data-files-browser.component';
import { InstanceAddEditComponent } from './components/instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './components/instance-browser/instance-browser.component';
import { InstanceHistoryComponent } from './components/instance-history/instance-history.component';
import { ProcessConfigurationComponent } from './components/process-configuration/process-configuration.component';

const INSTANCE_ROUTES: Route[] = [
  {
    path: 'browser/:name',
    component: InstanceBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instances (${params["name"]})', header: 'Instances' }
  },
  {
    path: 'add/:group',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance (${params["group"]})', header: 'Add Instance' }
  },
  {
    path: 'edit/:group/:uuid',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance (${params["group"]} - ${params["uuid"]})', header: 'Edit Instance' }
  },
  {
    path: 'overview/:group/:uuid',
    component: ProcessConfigurationComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Instance Overview (${params["group"]} - ${params["uuid"]})', header: 'Instance Overview' }
  },
  {
    path: 'datafiles/:group/:uuid/:version',
    component: DataFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Data Files (${params["group"]} - ${params["uuid"]})', header: 'Data Files' }
  },
  {
    path:'history/:group/:uuid',
    component:InstanceHistoryComponent,
    canActivate:[AuthGuard],
    canDeactivate:[CanDeactivateGuard],
    data: {title: 'History (${params["group"]} - ${params["uuid"]})', header: "Instance History"}
  }
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCE_ROUTES)],
  exports: [RouterModule],
})
export class InstanceRoutingModule { }
