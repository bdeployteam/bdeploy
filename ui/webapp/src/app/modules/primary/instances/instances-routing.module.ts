import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ScopedReadGuard } from '../../core/guards/scoped-read.guard';
import { ScopedWriteGuard } from '../../core/guards/scoped-write.guard';
import { InstancesBrowserComponent as BrowserComponent } from './components/browser/browser.component';
import { ConfigurationComponent } from './components/configuration/configuration.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { HistoryComponent } from './components/history/history.component';

const INSTANCES_ROUTES: Route[] = [
  { path: 'browser/:group', component: BrowserComponent, canActivate: [ScopedReadGuard] },
  { path: 'dashboard/:group/:instance', component: DashboardComponent, canActivate: [ScopedReadGuard] },
  { path: 'configuration/:group/:instance', component: ConfigurationComponent, canActivate: [ScopedWriteGuard] },
  { path: 'history/:group/:instance', component: HistoryComponent, canActivate: [ScopedReadGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(INSTANCES_ROUTES)],
  exports: [RouterModule],
})
export class InstancesRoutingModule {}
