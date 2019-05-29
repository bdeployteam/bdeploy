import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './guards/authentication.guard';
import { CanDeactivateGuard } from './guards/can-deactivate.guard';
import { HiveBrowserComponent } from './hive-browser/hive-browser.component';
import { InstanceAddEditComponent } from './instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './instance-browser/instance-browser.component';
import { InstanceGroupAddEditComponent } from './instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './instance-group-browser/instance-group-browser.component';
import { LoginComponent } from './login/login.component';
import { ProcessConfigurationComponent } from './process-configuration/process-configuration.component';
import { ProductsComponent } from './products/products.component';
import { SoftwareRepoAddEditComponent } from './software-repo-add-edit/software-repo-add-edit.component';
import { SoftwareRepositoriesBrowserComponent } from './software-repositories-browser/software-repositories-browser.component';
import { SoftwareRepositoryComponent } from './software-repository/software-repository.component';
import { UpdateBrowserComponent } from './update-browser/update-browser.component';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'instancegroupbrowser',
  },
  {
    path: 'login',
    component: LoginComponent,
    data: { title: 'Login' }
  },
  {
    path: 'instancegroupbrowser',
    component: InstanceGroupBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instance Groups' }
  },
  {
    path: 'instancegroupadd',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance Group' }
  },
  {
    path: 'instancegroupedit/:name',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance Group (${params["name"]})' }
  },
  {
    path: 'hivebrowser',
    component: HiveBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Hive Browser' }
  },
  {
    path: 'instancebrowser/:name',
    component: InstanceBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instances (${params["name"]})' }
  },
  {
    path: 'instanceadd/:group',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance (${params["group"]})' }
  },
  {
    path: 'instanceedit/:group/:uuid',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance (${params["group"]} - ${params["uuid"]})' }
  },
  {
    path: 'processconfiguration/:group/:uuid',
    component: ProcessConfigurationComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Process Configuration (${params["group"]} - ${params["uuid"]})' }
  },
  {
    path: 'products/:group',
    component: ProductsComponent,
    canActivate: [AuthGuard],
    data: { title: 'Products (${params["group"]})' }
  },
  {
    path: 'softwarerepositoriesbrowser',
    component: SoftwareRepositoriesBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Software Repositories' }
  },
  {
    path: 'softwarerepoadd',
    component: SoftwareRepoAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Software Repository' }
  },
  {
    path: 'softwarerepoedit/:name',
    component: SoftwareRepoAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Software Repository (${params["name"]})' }
  },
  {
    path: 'softwarerepository/:name',
    component: SoftwareRepositoryComponent,
    canActivate: [AuthGuard],
    data: { title: 'Software Repository (${params["name"]})' }
  },
  {
    path: 'updatebrowser',
    component: UpdateBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Browser available system versions' }
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
