import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTreeModule } from '@angular/material/tree';
import { CoreModule } from '../../core/core.module';
import { InstancesModule as PrimaryInstancesModule } from '../../primary/instances/instances.module';
import { AddControlGroupComponent } from './components/add-control-group/add-control-group.component';
import { AddDataFileComponent } from './components/add-data-file/add-data-file.component';
import { AddInstanceComponent } from './components/add-instance/add-instance.component';
import { AddProcessComponent } from './components/add-process/add-process.component';
import { AppTemplateNameComponent } from './components/add-process/app-template-name/app-template-name.component';
import { BulkControlComponent } from './components/bulk-control/bulk-control.component';
import { BulkManipulationComponent } from './components/bulk-manipulation/bulk-manipulation.component';
import { UpdateProductComponent } from './components/bulk-manipulation/update-product/update-product.component';
import { ConfigDescCardsComponent } from './components/config-desc-cards/config-desc-cards.component';
import { ConfigDescElementComponent } from './components/config-desc-element/config-desc-element.component';
import { DataFileEditorComponent } from './components/data-file-editor/data-file-editor.component';
import { DataFileViewerComponent } from './components/data-file-viewer/data-file-viewer.component';
import { DataFilesBulkManipulationComponent } from './components/data-files-buld-maipulation/data-files-bulk-manipulation.component';
import { EditControlGroupComponent } from './components/edit-control-group/edit-control-group.component';
import { ConfigureEndpointsComponent } from './components/edit-process-overview/configure-endpoints/configure-endpoints.component';
import { ConfigProcessHeaderComponent } from './components/edit-process-overview/configure-process/config-process-header/config-process-header.component';
import { ConfigProcessParamGroupComponent } from './components/edit-process-overview/configure-process/config-process-param-group/config-process-param-group.component';
import { ConfigureProcessComponent } from './components/edit-process-overview/configure-process/configure-process.component';
import { EditProcessOverviewComponent } from './components/edit-process-overview/edit-process-overview.component';
import { MoveProcessComponent } from './components/edit-process-overview/move-process/move-process.component';
import { HistoryCompareSelectComponent } from './components/history-compare-select/history-compare-select.component';
import { HistoryCompareComponent } from './components/history-compare/history-compare.component';
import { HistoryDiffFieldComponent } from './components/history-diff-field/history-diff-field.component';
import { HistoryEntryComponent } from './components/history-entry/history-entry.component';
import { HistoryHeaderConfigComponent } from './components/history-header-config/history-header-config.component';
import { HistoryProcessConfigComponent } from './components/history-process-config/history-process-config.component';
import { HistoryVariablesConfigComponent } from './components/history-variables-config/history-variables-config.component';
import { HistoryViewComponent } from './components/history-view/history-view.component';
import { AttributesComponent } from './components/instance-settings/attributes/attributes.component';
import { BannerComponent } from './components/instance-settings/banner/banner.component';
import { ColorSelectGroupComponent } from './components/instance-settings/banner/color-select-group/color-select-group.component';
import { ColorSelectComponent } from './components/instance-settings/banner/color-select/color-select.component';
import { CompareComponent } from './components/instance-settings/config-files/compare/compare.component';
import { ConfigFilesComponent } from './components/instance-settings/config-files/config-files.component';
import { DeleteActionComponent } from './components/instance-settings/config-files/delete-action/delete-action.component';
import { EditActionComponent } from './components/instance-settings/config-files/edit-action/edit-action.component';
import { EditorComponent } from './components/instance-settings/config-files/editor/editor.component';
import { ProductSyncComponent } from './components/instance-settings/config-files/product-sync/product-sync.component';
import { RenameActionComponent } from './components/instance-settings/config-files/rename-action/rename-action.component';
import { EditConfigComponent } from './components/instance-settings/edit-config/edit-config.component';
import { ImportInstanceComponent } from './components/instance-settings/import-instance/import-instance.component';
import { InstanceSettingsComponent } from './components/instance-settings/instance-settings.component';
import { InstanceTemplatesComponent } from './components/instance-settings/instance-templates/instance-templates.component';
import { TemplateMessageDetailsComponent } from './components/instance-settings/instance-templates/template-message-details/template-message-details.component';
import { InstanceVariablesComponent } from './components/instance-settings/instance-variables/instance-variables.component';
import { NodesComponent } from './components/instance-settings/nodes/nodes.component';
import { PortShiftComponent } from './components/instance-settings/ports/port-shift/port-shift.component';
import { PortTypeCellComponent } from './components/instance-settings/ports/port-type-cell/port-type-cell.component';
import { PortsComponent } from './components/instance-settings/ports/ports.component';
import { ProductUpdateComponent } from './components/instance-settings/product-update/product-update.component';
import { UpdateActionComponent } from './components/instance-settings/product-update/update-action/update-action.component';
import { LocalChangesComponent } from './components/local-changes/local-changes.component';
import { LocalDiffComponent } from './components/local-changes/local-diff/local-diff.component';
import { NodeDetailsComponent } from './components/node-details/node-details.component';
import { ParamDescCardComponent } from './components/param-desc-card/param-desc-card.component';
import { ProcessConsoleComponent } from './components/process-console/process-console.component';
import { ProcessNativesComponent } from './components/process-natives/process-natives.component';
import { ProcessPortsComponent } from './components/process-ports/process-ports.component';
import { PinnedParameterValueComponent } from './components/process-status/pinned-parameter-value/pinned-parameter-value.component';
import { ProbeStatusComponent } from './components/process-status/probe-status/probe-status.component';
import { ProcessStatusComponent } from './components/process-status/process-status.component';
import { ProductVersionDetailsCellComponent } from './components/product-version-details-cell/product-version-details-cell.component';
import { VariableDescCardComponent } from './components/variable-desc-card/variable-desc-card.component';
import { InstancesRoutingModule } from './instances-routing.module';
import { CustomNodeFilterPipe, NodeFilterPipe } from './utils/filter-node';
import { AllowedValuesValidatorDirective } from './validators/allowed-values-validator.directive';
import { CfgFileNameValidatorDirective } from './validators/cfg-file-name-validator.directive';
import { EditCustomIdValidatorDirective } from './validators/edit-custom-id-validator.directive';
import { EditProcessNameValidatorDirective } from './validators/edit-process-name-validator.directive';
import { EditServerIssuesValidatorDirective } from './validators/edit-server-issues-validator.directive';

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
    HistoryVariablesConfigComponent,
    HistoryDiffFieldComponent,
    ConfigDescCardsComponent,
    HistoryHeaderConfigComponent,
    InstanceSettingsComponent,
    EditConfigComponent,
    AttributesComponent,
    LocalChangesComponent,
    LocalDiffComponent,
    NodesComponent,
    AddProcessComponent,
    AppTemplateNameComponent,
    EditProcessOverviewComponent,
    ConfigureProcessComponent,
    InstanceTemplatesComponent,
    TemplateMessageDetailsComponent,
    ConfigProcessHeaderComponent,
    EditProcessNameValidatorDirective,
    EditCustomIdValidatorDirective,
    EditServerIssuesValidatorDirective,
    CfgFileNameValidatorDirective,
    ConfigDescElementComponent,
    ConfigProcessParamGroupComponent,
    ParamDescCardComponent,
    BannerComponent,
    MoveProcessComponent,
    ImportInstanceComponent,
    ProductUpdateComponent,
    UpdateActionComponent,
    ConfigFilesComponent,
    DeleteActionComponent,
    EditActionComponent,
    EditorComponent,
    RenameActionComponent,
    ProductSyncComponent,
    CompareComponent,
    DataFileViewerComponent,
    PortsComponent,
    ConfigureEndpointsComponent,
    PortShiftComponent,
    BulkManipulationComponent,
    UpdateProductComponent,
    DataFileEditorComponent,
    ColorSelectComponent,
    ColorSelectGroupComponent,
    ProductVersionDetailsCellComponent,
    DataFilesBulkManipulationComponent,
    ProbeStatusComponent,
    AddControlGroupComponent,
    EditControlGroupComponent,
    AddDataFileComponent,
    NodeFilterPipe,
    CustomNodeFilterPipe,
    PinnedParameterValueComponent,
    InstanceVariablesComponent,
    PortTypeCellComponent,
    VariableDescCardComponent,
    AllowedValuesValidatorDirective,
  ],
  imports: [
    CommonModule,
    CoreModule,
    InstancesRoutingModule,
    PrimaryInstancesModule,
    MatProgressSpinnerModule,
    MatCardModule,
    MatExpansionModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    MatStepperModule,
    MatTreeModule,
    MatButtonModule,
    MatRadioModule,
    MatTabsModule,
  ],
})
export class InstancesModule {}
