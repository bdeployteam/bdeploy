import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './modules/core/components/login/login.component';
import { MessageboxComponent } from './modules/shared/components/messagebox/messagebox.component';
import { NotFoundGuard } from './modules/shared/guards/not-found.guard';

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
  },
  {
    path: 'configfiles',
    loadChildren: () => import('./modules/config-files/config-files.module').then(x => x.ConfigFilesModule)
  },
  {
    path: '**',
    component: MessageboxComponent, // This is a DUMMY! the NotFoundGuard will /always/ redirect
    canActivate: [NotFoundGuard]
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
