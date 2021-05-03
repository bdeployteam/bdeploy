import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';
import { BulkControlComponent } from './components/bulk-control/bulk-control.component';
import { HistoryCompareSelectComponent } from './components/history-compare-select/history-compare-select.component';
import { HistoryCompareComponent } from './components/history-compare/history-compare.component';
import { HistoryEntryComponent } from './components/history-entry/history-entry.component';
import { HistoryViewComponent } from './components/history-view/history-view.component';
import { InstanceSettingsComponent } from './components/instance-settings/instance-settings.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { ProcessConsoleComponent } from './components/process-console/process-console.component';
import { ProcessNativesComponent } from './components/process-natives/process-natives.component';
import { ProcessPortsComponent } from './components/process-ports/process-ports.component';
import { ProcessStatusComponent } from './components/process-status/process-status.component';

const INSTANCES_ROUTES: Route[] = [
  { path: 'add', component: AddInstanceComponent, canActivate: [ScopedWriteGuard] },
  { path: 'bulk', component: BulkControlComponent, canActivate: [ScopedWriteGuard] },
  { path: 'settings', component: InstanceSettingsComponent, canActivate: [ScopedWriteGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'node/:node', component: NodeDetailsComponent, canActivate: [ScopedReadGuard] },
  { path: 'process/:process', component: ProcessStatusComponent, canActivate: [ScopedReadGuard] },
  { path: 'process/:process/ports', component: ProcessPortsComponent, canActivate: [ScopedReadGuard] },
  { path: 'process/:process/natives', component: ProcessNativesComponent, canActivate: [ScopedReadGuard], data: { max: true } },
  { path: 'process/:process/console', component: ProcessConsoleComponent, canActivate: [ScopedReadGuard], data: { max: true } },
  { path: 'history/:index', component: HistoryEntryComponent, canActivate: [ScopedReadGuard] },
  { path: 'history/:index/view/:base', component: HistoryViewComponent, canActivate: [ScopedReadGuard], data: { max: true } },
  { path: 'history/:index/compare/:base/:compare', component: HistoryCompareComponent, canActivate: [ScopedReadGuard], data: { max: true } },
  { path: 'history/:index/select/:base', component: HistoryCompareSelectComponent, canActivate: [ScopedReadGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
