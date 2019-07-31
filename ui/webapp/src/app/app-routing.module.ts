import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ClientAppsComponent } from './client-apps/client-apps.component';
import { ConfigFilesBrowserComponent } from './config-files-browser/config-files-browser.component';
import { DataFilesBrowserComponent } from './data-files-browser/data-files-browser.component';
import { AuthGuard } from './guards/authentication.guard';
import { CanDeactivateGuard } from './guards/can-deactivate.guard';
import { HiveBrowserComponent } from './hive-browser/hive-browser.component';
import { InstanceAddEditComponent } from './instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './instance-browser/instance-browser.component';
import { InstanceGroupAddEditComponent } from './instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './instance-group-browser/instance-group-browser.component';
import { LoginComponent } from './login/login.component';
import { MasterCleanupComponent } from './master-cleanup/master-cleanup.component';
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
    redirectTo: 'instancegroup/browser',
  },
  {
    path: 'login',
    component: LoginComponent,
    data: { title: 'Login' }
  },
  {
    path: 'instancegroup/browser',
    component: InstanceGroupBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instance Groups' }
  },
  {
    path: 'instancegroup/add',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance Group' }
  },
  {
    path: 'instancegroup/edit/:name',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance Group (${params["name"]})', header: 'Edit Instance Group' }
  },
  {
    path: 'hive/browser',
    component: HiveBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Hive Browser' }
  },
  {
    path: 'instance/browser/:name',
    component: InstanceBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instances (${params["name"]})', header: 'Instances' }
  },
  {
    path: 'instance/add/:group',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance (${params["group"]})', header: 'Add Instance' }
  },
  {
    path: 'instance/edit/:group/:uuid',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance (${params["group"]} - ${params["uuid"]})', header: 'Edit Instance' }
  },
  {
    path: 'instance/overview/:group/:uuid',
    component: ProcessConfigurationComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Instance Overview (${params["group"]} - ${params["uuid"]})', header: 'Instance Overview' }
  },
  {
    path: 'configfiles/:group/:uuid/:version',
    component: ConfigFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Configuration Files (${params["group"]} - ${params["uuid"]})', header: 'Configuration Files' }
  },
  {
    path: 'datafiles/:group/:uuid/:version',
    component: DataFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Data Files (${params["group"]} - ${params["uuid"]})', header: 'Data Files' }
  },
  {
    path: 'products/:group',
    component: ProductsComponent,
    canActivate: [AuthGuard],
    data: { title: 'Manage Products (${params["group"]})', header: 'Manage Products' }
  },
  {
    path: 'softwarerepo/browser',
    component: SoftwareRepositoriesBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Software Repositories' }
  },
  {
    path: 'softwarerepo/add',
    component: SoftwareRepoAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Software Repository' }
  },
  {
    path: 'softwarerepo/edit/:name',
    component: SoftwareRepoAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Software Repository (${params["name"]})', header: 'Edit Software Repository' }
  },
  {
    path: 'softwarerepo/packages/:name',
    component: SoftwareRepositoryComponent,
    canActivate: [AuthGuard],
    data: { title: 'Software Packages (${params["name"]})', header: 'Software Packages' }
  },
  {
    path: 'systemsoftware',
    component: UpdateBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'System Software' }
  },
  {
    path: 'manualcleanup',
    component: MasterCleanupComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Manual Cleanup' }
  },
  {
    path: 'clientapps/:group',
    component: ClientAppsComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Client Applications (${params["group"]})', header: 'Client Applications' }
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
