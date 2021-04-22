import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { CoreModule } from '../../core/core.module';
import { InstancesBrowserComponent } from './components/browser/browser.component';
import { InstanceBannerHintComponent } from './components/browser/instance-banner-hint/instance-banner-hint.component';
import { InstanceProductVersionComponent } from './components/browser/instance-product-version/instance-product-version.component';
import { InstanceSyncCellComponent } from './components/browser/instance-sync-cell/instance-sync-cell.component';
import { ClientNodeComponent } from './components/dashboard/client-node/client-node.component';
import { ClientUsageGraphComponent } from './components/dashboard/client-node/usage-graph/usage-graph.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { ProcessOutdatedComponent } from './components/dashboard/process-outdated/process-outdated.component';
import { ProcessStatusIconComponent } from './components/dashboard/process-status-icon/process-status-icon.component';
import { NodeHeaderComponent } from './components/dashboard/server-node/header/header.component';
import { NodeProcessListComponent } from './components/dashboard/server-node/process-list/process-list.component';
import { ServerNodeComponent } from './components/dashboard/server-node/server-node.component';
import { NodeStatePanelComponent } from './components/dashboard/server-node/state-panel/state-panel.component';
import { InstancesRoutingModule } from './instances-routing.module';
import { ConfigurationComponent } from './components/configuration/configuration.component';
import { HistoryComponent } from './components/history/history.component';

@NgModule({
  declarations: [
    InstancesBrowserComponent,
    InstanceProductVersionComponent,
    InstanceBannerHintComponent,
    DashboardComponent,
    ServerNodeComponent,
    ClientNodeComponent,
    NodeHeaderComponent,
    NodeStatePanelComponent,
    NodeProcessListComponent,
    ProcessOutdatedComponent,
    ProcessStatusIconComponent,
    InstanceSyncCellComponent,
    ClientUsageGraphComponent,
    ConfigurationComponent,
    HistoryComponent,
  ],
  exports: [
    // for panels.
    NodeHeaderComponent,
    ProcessStatusIconComponent,
  ],
  imports: [CommonModule, CoreModule, InstancesRoutingModule, MatCardModule],
})
export class InstancesModule {}
