import { Routes } from '@angular/router';


import { AdminGuard } from './modules/core/guards/admin.guard';
import { AuthGuard } from './modules/core/guards/authentication.guard';
import { NotFoundGuard } from './modules/core/guards/not-found.guard';
import { ScopedAdminGuard } from './modules/core/guards/scoped-admin.guard';
import { ScopedReadGuard } from './modules/core/guards/scoped-read.guard';
import { ServerCentralGuard } from './modules/core/guards/server-central.guard';

export const APP_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'groups/browser',
  },
  {
    path: 'login',
    loadComponent: () => import('./modules/core/components/login/login.component').then(m => m.LoginComponent),
    data: { title: 'Login', header: 'Login' },
  },
  {
    path: 'admin',
    loadChildren: () => import('./modules/primary/admin/admin.routing').then((x) => x.ADMIN_ROUTES),
    canActivate: [AdminGuard],
  },
  {
    path: 'panels/admin',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/admin/admin.routing').then((m) => m.ADMIN_PANEL_ROUTES),
    canActivate: [AdminGuard],
  },
  {
    path: 'groups',
    loadChildren: () => import('./modules/primary/groups/groups.routing').then((x) => x.GROUPS_ROUTES),
    canActivate: [AuthGuard], // Permission is granted to all users to view the things in here.
  },
  {
    path: 'panels/groups',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/groups/groups.routing').then((x) => x.GROUPS_PANEL_ROUTES),
    canActivate: [AuthGuard],
  },
  {
    path: 'repositories',
    loadChildren: () => import('./modules/primary/repositories/repositories.routing').then((x) => x.REPOS_ROUTES),
    canActivate: [AuthGuard],
  },
  {
    path: 'panels/repositories',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/repositories/repositories.routing').then((x) => x.REPOS_PANEL_ROUTES),
    canActivate: [AuthGuard],
  },
  {
    path: 'reports',
    loadChildren: () => import('./modules/primary/reports/reports.routing').then((x) => x.REPORTS_ROUTES),
    canActivate: [AuthGuard],
  },
  {
    path: 'panels/reports',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/reports/reports.routing').then((x) => x.REPORTS_PANEL_ROUTES),
    canActivate: [AuthGuard],
  },
  {
    path: 'instances',
    loadChildren: () => import('./modules/primary/instances/instances.routing').then((x) => x.INSTANCES_ROUTES),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'panels/instances',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/instances/instances.routing').then((x) => x.INSTANCES_PANEL_ROUTES),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'products',
    loadChildren: () => import('./modules/primary/products/products.routing').then((x) => x.PRODUCTS_ROUTES),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'panels/products',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/products/products.routing').then((x) => x.PRODUCTS_PANEL_ROUTES),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'systems',
    loadChildren: () => import('./modules/primary/systems/systems.routing').then((x) => x.SYSTEMS_ROUTES),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'panels/systems',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/systems/systems.routing').then((x) => x.SYSTEMS_PANEL_ROUTES),
    canActivate: [AuthGuard, ScopedReadGuard],
  },
  {
    path: 'servers',
    loadChildren: () => import('./modules/primary/servers/servers.routing').then((x) => x.SERVERS_ROUTES),
    canActivate: [AuthGuard, ServerCentralGuard, ScopedAdminGuard],
  },
  {
    path: 'panels/servers',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/servers/servers.routing').then((x) => x.SERVERS_PANEL_ROUTES),
    canActivate: [AuthGuard, ScopedAdminGuard],
  },

  {
    path: 'panels/user',
    outlet: 'panel',
    loadChildren: () => import('./modules/panels/user/user.routing').then((x) => x.USER_PANEL_ROUTES),
    canActivate: [AuthGuard],
  },

  {
    path: '**',
    loadComponent: () => import('./modules/core/components/main-nav/main-nav.component').then(m => m.MainNavComponent), // This is a DUMMY! the NotFoundGuard will /always/ redirect
    canActivate: [NotFoundGuard],
  },
];
