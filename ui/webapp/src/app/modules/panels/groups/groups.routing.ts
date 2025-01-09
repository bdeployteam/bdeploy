import { Route } from '@angular/router';
import { AdminGuard } from '../../core/guards/admin.guard';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedAdminGuard } from '../../core/guards/scoped-admin.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';










export const GROUPS_PANEL_ROUTES: Route[] = [
  {
    path: 'add',
    loadComponent: () => import('./components/add-group/add-group.component').then(m => m.AddGroupComponent),
    canActivate: [AdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings',
    loadComponent: () => import('./components/settings/settings.component').then(m => m.SettingsComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/edit',
    loadComponent: () => import('./components/settings/edit/edit.component').then(m => m.EditComponent),
    canActivate: [ScopedAdminGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings/attributes/values',
    loadComponent: () => import('./components/settings/attribute-values/attribute-values.component').then(m => m.AttributeValuesComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/attributes/definitions',
    loadComponent: () => import('./components/settings/attribute-definitions/attribute-definitions.component').then(m => m.AttributeDefinitionsComponent),
    canActivate: [ScopedAdminGuard],
  },
  {
    path: 'settings/permissions',
    loadComponent: () => import('./components/settings/permissions/permissions.component').then(m => m.PermissionsComponent),
    canActivate: [ScopedAdminGuard],
    data: { max: true },
  },
  {
    path: 'client/:app',
    loadComponent: () => import('./components/client-detail/client-detail.component').then(m => m.ClientDetailComponent),
  },
  {
    path: 'endpoint/:app/:endpoint',
    loadComponent: () => import('./components/process-ui-inline/process-ui-inline.component').then(m => m.ProcessUiInlineComponent),
    data: { max: true },
  },
  {
    path: 'endpoint-detail/:app/:endpoint',
    loadComponent: () => import('./components/endpoint-detail/endpoint-detail.component').then(m => m.EndpointDetailComponent),
  },
];
