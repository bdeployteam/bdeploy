import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './modules/core/components/login/login.component';
import { MainNavComponent } from './modules/core/components/main-nav/main-nav.component';
import { AdminGuard } from './modules/core/guards/admin.guard';
import { AuthGuard } from './modules/core/guards/authentication.guard';
import { NotFoundGuard } from './modules/core/guards/not-found.guard';
import { ScopedAdminGuard } from './modules/core/guards/scoped-admin.guard';
import { ScopedReadGuard } from './modules/core/guards/scoped-read.guard';
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
    loadChildren: () => import('./modules/primary/admin/admin.module').then((x) => x.AdminModule),
    canActivate: [AdminGuard],
  },
  {
    path: 'panels/admin',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/admin/admin.module').then((m) => m.AdminModule),
    canActivate: [AdminGuard],
  },
  {
    path: 'groups',
    loadChildren: () => import('./modules/primary/groups/groups.module').then((x) => x.GroupsModule),
    canActivate: [AuthGuard], // Permission is granted to all users to view the things in here.
  },
  {
    path: 'panels/groups',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/groups/groups.module').then((x) => x.GroupsModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'repositories',
    loadChildren: () => import('./modules/primary/repositories/repositories.module').then((x) => x.RepositoriesModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'panels/repositories',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/repositories/repositories.module').then((x) => x.RepositoriesModule),
    canActivate: [AuthGuard],
  },
  {
    path: 'instances',
    loadChildren: () => import('./modules/primary/instances/instances.module').then((x) => x.InstancesModule),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'panels/instances',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/instances/instances.module').then((x) => x.InstancesModule),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'products',
    loadChildren: () => import('./modules/primary/products/products.module').then((x) => x.ProductsModule),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'panels/products',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/products/products.module').then((x) => x.ProductsModule),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'servers',
    loadChildren: () => import('./modules/primary/servers/servers.module').then((x) => x.ServersModule),
    canActivate: [AuthGuard, ServerCentralGuard, ScopedAdminGuard],
  },
  {
    path: 'panels/servers',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/servers/servers.module').then((x) => x.ServersModule),
    canActivate: [AuthGuard, ScopedAdminGuard],
  },

  {
    path: 'panels/user',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/user/user.module').then((x) => x.UserModule),
    canActivate: [AuthGuard],
  },

  {
    path: '**',
    component: MainNavComponent, // This is a DUMMY! the NotFoundGuard will /always/ redirect
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
