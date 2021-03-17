import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './modules/core/components/login/login.component';
import { MessageboxComponent } from './modules/core/components/messagebox/messagebox.component';
import { AdminGuard } from './modules/core/guards/admin.guard';
import { AuthGuard } from './modules/core/guards/authentication.guard';
import { NotFoundGuard } from './modules/core/guards/not-found.guard';
import { ServerCentralGuard } from './modules/core/guards/server-central.guard';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'groups/browser',
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
  {
    path: 'groups',
    loadChildren: () => import('./modules/primary/groups/groups.module').then((x) => x.GroupsModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'panels/groups',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/groups/groups.module').then((x) => x.GroupsModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'instances',
    loadChildren: () => import('./modules/primary/instances/instances.module').then((x) => x.InstancesModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'panels/instances',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/instances/instances.module').then((x) => x.InstancesModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'products',
    loadChildren: () => import('./modules/primary/products/products.module').then((x) => x.ProductsModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'panels/products',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/products/products.module').then((x) => x.ProductsModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'servers',
    loadChildren: () => import('./modules/primary/servers/servers.module').then((x) => x.ServersModule),
    canActivate: [AuthGuard, ServerCentralGuard],
  },
  {
    path: 'panels/servers',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/servers/servers.module').then((x) => x.ServersModule),
    canActivate: [AuthGuard],
  },

  {
    path: 'panels/user',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/user/user.module').then((x) => x.UserModule),
    canActivate: [AuthGuard],
  },

  // LEGACY ROUTES
  {
    path: 'l/instancegroup',
    loadChildren: () => import('./modules/legacy/instance-group/instance-group.module').then((x) => x.InstanceGroupModule),
  },
  {
    // TESTING
    path: 'ig-side',
    outlet: 'panel',
    loadChildren: () => import('./modules/legacy/instance-group/instance-group.module').then((x) => x.InstanceGroupModule),
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
  imports: [
    RouterModule.forRoot(routes, {
      useHash: true,
      relativeLinkResolution: 'corrected',
      preloadingStrategy: PreloadAllModules,
    }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule {}
