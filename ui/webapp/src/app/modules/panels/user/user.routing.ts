import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';






export const USER_PANEL_ROUTES: Route[] = [
  { path: 'themes', loadComponent: () => import('./components/themes/themes.component').then(m => m.ThemesComponent) },
  { path: 'settings', loadComponent: () => import('./components/settings/settings.component').then(m => m.SettingsComponent) },
  {
    path: 'settings/edit',
    loadComponent: () => import('./components/settings/edit/edit.component').then(m => m.EditComponent),
    canDeactivate: [DirtyDialogGuard],
  },
  { path: 'settings/password', loadComponent: () => import('./components/settings/password/password.component').then(m => m.PasswordComponent) },
  { path: 'settings/token', loadComponent: () => import('./components/settings/token/token.component').then(m => m.TokenComponent) },
];
