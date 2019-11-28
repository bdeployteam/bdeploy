import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './modules/core/components/login/login.component';

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
    path: 'instancegroup',
    loadChildren: () => import('./modules/instance-group/instance-group.module').then(x => x.InstanceGroupModule)
  },
  {
    path: 'instance',
    loadChildren: () => import('./modules/instance/instance.module').then(x => x.InstanceModule)
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
