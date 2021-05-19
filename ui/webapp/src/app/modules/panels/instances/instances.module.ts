import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CoreModule } from '../../core/core.module';
import { InstancesModule as PrimaryInstancesModule } from '../../primary/instances/instances.module';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';
import { BulkControlComponent } from './components/bulk-control/bulk-control.component';
import { ConfigDescCardsComponent } from './components/config-desc-cards/config-desc-cards.component';
import { HistoryCompareSelectComponent } from './components/history-compare-select/history-compare-select.component';
import { HistoryCompareComponent } from './components/history-compare/history-compare.component';
import { HistoryDiffFieldComponent } from './components/history-diff-field/history-diff-field.component';
import { HistoryEntryComponent } from './components/history-entry/history-entry.component';
import { HistoryHeaderConfigComponent } from './components/history-header-config/history-header-config.component';
import { HistoryProcessConfigComponent } from './components/history-process-config/history-process-config.component';
import { HistoryViewComponent } from './components/history-view/history-view.component';
import { AttributesComponent } from './components/instance-settings/attributes/attributes.component';
import { EditConfigComponent } from './components/instance-settings/edit-config/edit-config.component';
import { InstanceSettingsComponent } from './components/instance-settings/instance-settings.component';
import { MaintenanceComponent } from './components/instance-settings/maintenance/maintenance.component';
import { LocalChangesComponent } from './components/local-changes/local-changes.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { ProcessConsoleComponent } from './components/process-console/process-console.component';
import { ProcessNativesComponent } from './components/process-natives/process-natives.component';
import { ProcessPortsComponent } from './components/process-ports/process-ports.component';
import { ProcessStatusComponent } from './components/process-status/process-status.component';
import { InstancesRoutingModule } from './instances-routing.module';
import { LocalDiffComponent } from './components/local-changes/local-diff/local-diff.component';
import { NodesComponent } from './components/instance-settings/nodes/nodes.component';
import { AddProcessComponent } from './components/add-process/add-process.component';
import { AppTemplateNameComponent } from './components/add-process/app-template-name/app-template-name.component';
import { EditProcessOverviewComponent } from './components/edit-process-overview/edit-process-overview.component';
import { ConfigureProcessComponent } from './components/edit-process-overview/configure-process/configure-process.component';

@NgModule({
  declarations: [
    AddInstanceComponent,
    ProcessStatusComponent,
    NodeDetailsComponent,
    ProcessPortsComponent,
    ProcessNativesComponent,
    ProcessConsoleComponent,
    BulkControlComponent,
    HistoryEntryComponent,
    HistoryCompareSelectComponent,
    HistoryCompareComponent,
    HistoryViewComponent,
    HistoryProcessConfigComponent,
    HistoryDiffFieldComponent,
    ConfigDescCardsComponent,
    HistoryHeaderConfigComponent,
    InstanceSettingsComponent,
    EditConfigComponent,
    MaintenanceComponent,
    AttributesComponent,
    LocalChangesComponent,
    LocalDiffComponent,
    NodesComponent,
    AddProcessComponent,
    AppTemplateNameComponent,
    EditProcessOverviewComponent,
    ConfigureProcessComponent,
  ],
  imports: [CommonModule, CoreModule, InstancesRoutingModule, PrimaryInstancesModule, MatProgressSpinnerModule, MatCardModule],
})
export class InstancesModule {}
