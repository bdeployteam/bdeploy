import { Route } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';


export const INSTANCES_PANEL_ROUTES: Route[] = [
  {
    path: 'add',
    loadComponent: () => import('./components/add-instance/add-instance.component').then(m => m.AddInstanceComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'bulk-control',
    loadComponent: () => import('./components/bulk-control/bulk-control.component').then(m => m.BulkControlComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'bulk-manip',
    loadComponent: () => import('./components/bulk-manipulation/bulk-manipulation.component').then(m => m.BulkManipulationComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'changes',
    loadComponent: () => import('./components/local-changes/local-changes.component').then(m => m.LocalChangesComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'changes/diff',
    loadComponent: () => import('./components/local-changes/local-diff/local-diff.component').then(m => m.LocalDiffComponent),
    canActivate: [ScopedWriteGuard],
    data: { max: true },
  },
  {
    path: 'settings',
    loadComponent: () => import('./components/instance-settings/instance-settings.component').then(m => m.InstanceSettingsComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/config',
    loadComponent: () => import('./components/instance-settings/edit-config/edit-config.component').then(m => m.EditConfigComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings/attributes',
    loadComponent: () => import('./components/instance-settings/attributes/attributes.component').then(m => m.AttributesComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/nodes',
    loadComponent: () => import('./components/instance-settings/nodes/nodes.component').then(m => m.NodesComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings/variables',
    loadComponent: () => import('./components/instance-settings/instance-variables/instance-variables.component').then(m => m.InstanceVariablesComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'settings/templates',
    loadComponent: () => import('./components/instance-settings/instance-templates/instance-templates.component').then(m => m.InstanceTemplatesComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/banner',
    loadComponent: () => import('./components/instance-settings/banner/banner.component').then(m => m.BannerComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/ports',
    loadComponent: () => import('./components/instance-settings/ports/ports.component').then(m => m.PortsComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/import',
    loadComponent: () => import('./components/instance-settings/import-instance/import-instance.component').then(m => m.ImportInstanceComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/product',
    loadComponent: () => import('./components/instance-settings/product-update/product-update.component').then(m => m.ProductUpdateComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/config-files',
    loadComponent: () => import('./components/instance-settings/config-files/config-files.component').then(m => m.ConfigFilesComponent),
    canActivate: [ScopedWriteGuard],
    data: { max: true },
  },
  {
    path: 'settings/config-files/:file',
    loadComponent: () => import('./components/instance-settings/config-files/editor/editor.component').then(m => m.EditorComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'settings/config-files/compare/:file',
    loadComponent: () => import('./components/instance-settings/config-files/compare/compare.component').then(m => m.CompareComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'node/:node',
    loadComponent: () => import('./components/node-details/node-details.component').then(m => m.NodeDetailsComponent),
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'process/:process',
    loadComponent: () => import('./components/process-status/process-status.component').then(m => m.ProcessStatusComponent),
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'multi-node-process/:process',
    loadComponent: () => import('./components/multi-node-process-status/multi-process-status.component').then(m => m.MultiProcessStatusComponent),
    canActivate: [ScopedReadGuard]
  },
  {
    path: 'process/:process/ports',
    loadComponent: () => import('./components/process-ports/process-ports.component').then(m => m.ProcessPortsComponent),
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'process/:process/natives',
    loadComponent: () => import('./components/process-natives/process-natives.component').then(m => m.ProcessNativesComponent),
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'process/:process/console',
    loadComponent: () => import('./components/process-console/process-console.component').then(m => m.ProcessConsoleComponent),
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'history/:key',
    loadComponent: () => import('./components/history-entry/history-entry.component').then(m => m.HistoryEntryComponent),
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'history/:key/view/:base',
    loadComponent: () => import('./components/history-view/history-view.component').then(m => m.HistoryViewComponent),
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'history/:key/compare/:base/:compare',
    loadComponent: () => import('./components/history-compare/history-compare.component').then(m => m.HistoryCompareComponent),
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'history/:key/select/:base',
    loadComponent: () => import('./components/history-compare-select/history-compare-select.component').then(m => m.HistoryCompareSelectComponent),
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'config/add-process/:node',
    loadComponent: () => import('./components/add-process/add-process.component').then(m => m.AddProcessComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/add-control-group/:node',
    loadComponent: () => import('./components/add-control-group/add-control-group.component').then(m => m.AddControlGroupComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard]
  },
  {
    path: 'config/edit-control-group/:node/:cgrp',
    loadComponent: () => import('./components/edit-control-group/edit-control-group.component').then(m => m.EditControlGroupComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard]
  },
  {
    path: 'config/process/:node/:process',
    loadComponent: () => import('./components/edit-process-overview/edit-process-overview.component').then(m => m.EditProcessOverviewComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/process/:node/:process/move',
    loadComponent: () => import('./components/edit-process-overview/move-process/move-process.component').then(m => m.MoveProcessComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/process/:node/:process/edit',
    loadComponent: () => import('./components/edit-process-overview/configure-process/configure-process.component').then(m => m.ConfigureProcessComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'config/process/:node/:process/endpoints',
    loadComponent: () => import('./components/edit-process-overview/configure-endpoints/configure-endpoints.component').then(m => m.ConfigureEndpointsComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'files/bulk-manip',
    loadComponent: () => import('./components/files-bulk-maipulation/files-bulk-manipulation.component').then(m => m.FilesBulkManipulationComponent),
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'files/:node/:file/view',
    loadComponent: () => import('./components/file-viewer/file-viewer.component').then(m => m.FileViewerComponent),
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'files/:node/:file/edit',
    loadComponent: () => import('./components/data-file-editor/data-file-editor.component').then(m => m.DataFileEditorComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'data-files/add',
    loadComponent: () => import('./components/add-data-file/add-data-file.component').then(m => m.AddDataFileComponent),
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];
