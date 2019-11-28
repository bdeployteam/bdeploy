import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ClientAppsComponent } from './client-apps/client-apps.component';
import { ConfigFilesBrowserComponent } from './config-files-browser/config-files-browser.component';
import { DataFilesBrowserComponent } from './data-files-browser/data-files-browser.component';
import { InstanceAddEditComponent } from './instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './instance-browser/instance-browser.component';
import { LoginComponent } from './modules/core/components/login/login.component';
import { AuthGuard } from './modules/shared/guards/authentication.guard';
import { CanDeactivateGuard } from './modules/shared/guards/can-deactivate.guard';
import { ProcessConfigurationComponent } from './process-configuration/process-configuration.component';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'instancegroup/browser',
  },
  {
    path: 'login',
    component: LoginComponent,
    data: { title: 'Login', header: 'Login' }
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
    path: 'instance/configfiles/:group/:uuid/:version',
    component: ConfigFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Configuration Files (${params["group"]} - ${params["uuid"]})', header: 'Configuration Files' }
  },
  {
    path: 'instance/datafiles/:group/:uuid/:version',
    component: DataFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Data Files (${params["group"]} - ${params["uuid"]})', header: 'Data Files' }
  },
  {
    path: 'clientapps/:group',
    component: ClientAppsComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Client Applications (${params["group"]})', header: 'Client Applications' }
  },
  {
    path: 'instancegroup',
    loadChildren: () => import('./modules/instance-group/instance-group.module').then(x => x.InstanceGroupModule)
  },
  {
    path: 'admin',
    loadChildren: () => import('./modules/admin/admin.module').then(x => x.AdminModule)
  },
  {
    path: 'softwarerepo',
    loadChildren: () => import('./modules/repositories/repositories.module').then(x => x.RepositoriesModule)
  },
  {
    path: 'servers',
    loadChildren: () => import('./modules/servers/servers.module').then(x => x.ServersModule)
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
