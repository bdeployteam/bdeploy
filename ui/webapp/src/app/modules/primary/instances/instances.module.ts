import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { CoreModule } from '../../core/core.module';
import { InstancesBrowserComponent } from './components/browser/browser.component';
import { InstanceBannerHintComponent } from './components/browser/instance-banner-hint/instance-banner-hint.component';
import { InstanceProductVersionComponent } from './components/browser/instance-product-version/instance-product-version.component';
import { ConfigNodeComponent } from './components/configuration/config-node/config-node.component';
import { ControlGroupComponent } from './components/configuration/config-node/control-group/control-group.component';
import { ConfigurationComponent } from './components/configuration/configuration.component';
import { ClientNodeComponent } from './components/dashboard/client-node/client-node.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { ProcessOutdatedComponent } from './components/dashboard/process-outdated/process-outdated.component';
import { ProcessStatusIconComponent } from './components/dashboard/process-status-icon/process-status-icon.component';
import { NodeHeaderComponent } from './components/dashboard/server-node/header/header.component';
import { NodeProcessListComponent } from './components/dashboard/server-node/process-list/process-list.component';
import { ServerNodeComponent } from './components/dashboard/server-node/server-node.component';
import { NodeStatePanelComponent } from './components/dashboard/state-panel/state-panel.component';
import { DataFilesComponent } from './components/data-files/data-files.component';
import { HistoryStateColumnComponent } from './components/history-state-column/history-state-column.component';
import { HistoryComponent } from './components/history/history.component';
import { PortStatusColumnComponent } from './components/port-status-column/port-status-column.component';
import { ProcessNameAndOsComponent } from './components/process-name-and-os/process-name-and-os.component';
import { RatingStatusColumnComponent } from './components/rating-status-column/rating-status-column.component';
import { StateStatusColumnComponent } from './components/state-status-column/state-status-column.component';
import { InstancesRoutingModule } from './instances-routing.module';

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
    ConfigurationComponent,
    HistoryComponent,
    ConfigNodeComponent,
    DataFilesComponent,
    ProcessNameAndOsComponent,
    PortStatusColumnComponent,
    HistoryStateColumnComponent,
    RatingStatusColumnComponent,
    StateStatusColumnComponent,
    ControlGroupComponent,
  ],
  exports: [
    // for panels.
    NodeHeaderComponent,
    ProcessStatusIconComponent,
  ],
  imports: [
    CommonModule,
    CoreModule,
    InstancesRoutingModule,
    MatCardModule,
    MatStepperModule,
    MatButtonToggleModule,
    MatProgressSpinnerModule,
    MatExpansionModule,
  ],
})
export class InstancesModule {}
