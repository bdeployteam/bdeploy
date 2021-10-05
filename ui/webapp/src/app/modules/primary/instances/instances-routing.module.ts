import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { DirtyDialogGuard } from '../../core/guards/dirty-dialog.guard';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { InstancesBrowserComponent as BrowserComponent } from './components/browser/browser.component';
import { ConfigurationComponent } from './components/configuration/configuration.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { DataFilesComponent } from './components/data-files/data-files.component';
import { HistoryComponent } from './components/history/history.component';

const INSTANCES_ROUTES: Route[] = [
  { path: 'browser/:group', component: BrowserComponent, canActivate: [ScopedReadGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'dashboard/:group/:instance', component: DashboardComponent, canActivate: [ScopedReadGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'configuration/:group/:instance', component: ConfigurationComponent, canActivate: [ScopedWriteGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'history/:group/:instance', component: HistoryComponent, canActivate: [ScopedReadGuard], canDeactivate: [DirtyDialogGuard] },
  { path: 'data-files/:group/:instance', component: DataFilesComponent, canActivate: [ScopedReadGuard], canDeactivate: [DirtyDialogGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
