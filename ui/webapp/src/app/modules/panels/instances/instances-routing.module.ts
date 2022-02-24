import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { AddControlGroupComponent } from './components/add-control-group/add-control-group.component';
import { AddDataFileComponent } from './components/add-data-file/add-data-file.component';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';
import { AddProcessComponent } from './components/add-process/add-process.component';
import { BulkControlComponent } from './components/bulk-control/bulk-control.component';
import { BulkManipulationComponent } from './components/bulk-manipulation/bulk-manipulation.component';
import { UpdateProductComponent } from './components/bulk-manipulation/update-product/update-product.component';
import { DataFileEditorComponent } from './components/data-file-editor/data-file-editor.component';
import { DataFileViewerComponent } from './components/data-file-viewer/data-file-viewer.component';
import { DataFilesBulkManipulationComponent } from './components/data-files-buld-maipulation/data-files-bulk-manipulation.component';
import { EditControlGroupComponent } from './components/edit-control-group/edit-control-group.component';
import { ConfigureEndpointsComponent } from './components/edit-process-overview/configure-endpoints/configure-endpoints.component';
import { ConfigureProcessComponent } from './components/edit-process-overview/configure-process/configure-process.component';
import { EditProcessOverviewComponent } from './components/edit-process-overview/edit-process-overview.component';
import { MoveProcessComponent } from './components/edit-process-overview/move-process/move-process.component';
import { HistoryCompareSelectComponent } from './components/history-compare-select/history-compare-select.component';
import { HistoryCompareComponent } from './components/history-compare/history-compare.component';
import { HistoryEntryComponent } from './components/history-entry/history-entry.component';
import { HistoryViewComponent } from './components/history-view/history-view.component';
import { AttributesComponent } from './components/instance-settings/attributes/attributes.component';
import { BannerComponent } from './components/instance-settings/banner/banner.component';
import { CompareComponent } from './components/instance-settings/config-files/compare/compare.component';
import { ConfigFilesComponent } from './components/instance-settings/config-files/config-files.component';
import { EditorComponent } from './components/instance-settings/config-files/editor/editor.component';
import { EditConfigComponent } from './components/instance-settings/edit-config/edit-config.component';
import { ImportInstanceComponent } from './components/instance-settings/import-instance/import-instance.component';
import { InstanceSettingsComponent } from './components/instance-settings/instance-settings.component';
import { InstanceTemplatesComponent } from './components/instance-settings/instance-templates/instance-templates.component';
import { NodesComponent } from './components/instance-settings/nodes/nodes.component';
import { PortsComponent } from './components/instance-settings/ports/ports.component';
import { ProductUpdateComponent } from './components/instance-settings/product-update/product-update.component';
import { LocalChangesComponent } from './components/local-changes/local-changes.component';
import { LocalDiffComponent } from './components/local-changes/local-diff/local-diff.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { ProcessConsoleComponent } from './components/process-console/process-console.component';
import { ProcessNativesComponent } from './components/process-natives/process-natives.component';
import { ProcessPortsComponent } from './components/process-ports/process-ports.component';
import { ProcessStatusComponent } from './components/process-status/process-status.component';

const INSTANCES_ROUTES: Route[] = [
  {
    path: 'add',
    component: AddInstanceComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'bulk-control',
    component: BulkControlComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'bulk-manip',
    component: BulkManipulationComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'bulk-manip/update',
    component: UpdateProductComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'changes',
    component: LocalChangesComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'changes/diff',
    component: LocalDiffComponent,
    canActivate: [ScopedWriteGuard],
    data: { max: true },
  },
  {
    path: 'settings',
    component: InstanceSettingsComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/config',
    component: EditConfigComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings/attributes',
    component: AttributesComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/nodes',
    component: NodesComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
  {
    path: 'settings/templates',
    component: InstanceTemplatesComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/banner',
    component: BannerComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/ports',
    component: PortsComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/import',
    component: ImportInstanceComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/product',
    component: ProductUpdateComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/config-files',
    component: ConfigFilesComponent,
    canActivate: [ScopedWriteGuard],
    data: { max: true },
  },
  {
    path: 'settings/config-files/:file',
    component: EditorComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'data-files/bulk-manip',
    component: DataFilesBulkManipulationComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'settings/config-files/compare/:file',
    component: CompareComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'node/:node',
    component: NodeDetailsComponent,
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'process/:process',
    component: ProcessStatusComponent,
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'process/:process/ports',
    component: ProcessPortsComponent,
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'process/:process/natives',
    component: ProcessNativesComponent,
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'process/:process/console',
    component: ProcessConsoleComponent,
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'history/:key',
    component: HistoryEntryComponent,
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'history/:key/view/:base',
    component: HistoryViewComponent,
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'history/:key/compare/:base/:compare',
    component: HistoryCompareComponent,
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'history/:key/select/:base',
    component: HistoryCompareSelectComponent,
    canActivate: [ScopedReadGuard],
  },
  {
    path: 'config/add-process/:node',
    component: AddProcessComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/add-control-group/:node',
    component: AddControlGroupComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/edit-control-group/:node/:cgrp',
    component: EditControlGroupComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/process/:node/:process',
    component: EditProcessOverviewComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/process/:node/:process/move',
    component: MoveProcessComponent,
    canActivate: [ScopedWriteGuard],
  },
  {
    path: 'config/process/:node/:process/edit',
    component: ConfigureProcessComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'config/process/:node/:process/endpoints',
    component: ConfigureEndpointsComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'data-files/:node/:file/view',
    component: DataFileViewerComponent,
    canActivate: [ScopedReadGuard],
    data: { max: true },
  },
  {
    path: 'data-files/:node/:file/edit',
    component: DataFileEditorComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
    data: { max: true },
  },
  {
    path: 'data-files/add',
    component: AddDataFileComponent,
    canActivate: [ScopedWriteGuard],
    canDeactivate: [DirtyDialogGuard],
  },
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
