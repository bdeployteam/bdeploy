import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './modules/core/components/login/login.component';
import { MessageboxComponent } from './modules/core/components/messagebox/messagebox.component';
import { AdminGuard } from './modules/core/guards/admin.guard';
import { NotFoundGuard } from './modules/core/guards/not-found.guard';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'l/instancegroup/browser',
  },
  {
    path: 'login',
    component: LoginComponent,
    data: { title: 'Login', header: 'Login' },
  },
  {
    path: 'admin',
    loadChildren: () => import('./modules/admin/admin.module').then((x) => x.AdminModule),
    canActivate: [AdminGuard],
  },

  // LEGACY ROUTES
  {
    path: 'l/instancegroup',
    loadChildren: () =>
      import('./modules/legacy/instance-group/instance-group.module').then((x) => x.InstanceGroupModule),
  },
  {
    // TESTING
    path: 'ig-side',
    outlet: 'panel',
    loadChildren: () =>
      import('./modules/legacy/instance-group/instance-group.module').then((x) => x.InstanceGroupModule),
  },
  {
    path: 'l/instance',
    loadChildren: () => import('./modules/legacy/instance/instance.module').then((x) => x.InstanceModule),
  },
  {
    path: 'l/softwarerepo',
    loadChildren: () => import('./modules/legacy/repositories/repositories.module').then((x) => x.RepositoriesModule),
  },
  {
    path: 'l/servers',
    loadChildren: () => import('./modules/legacy/servers/servers.module').then((x) => x.ServersModule),
  },
  {
    path: 'l/configfiles',
    loadChildren: () => import('./modules/legacy/config-files/config-files.module').then((x) => x.ConfigFilesModule),
  },
  // END LEGACY ROUTES

  {
    path: '**',
    component: MessageboxComponent, // This is a DUMMY! the NotFoundGuard will /always/ redirect
    canActivate: [NotFoundGuard],
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true, relativeLinkResolution: 'corrected' })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
