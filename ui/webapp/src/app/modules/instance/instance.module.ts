import { PortalModule } from '@angular/cdk/portal';
import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { DragulaModule } from 'ng2-dragula';
import { CoreModule } from '../core/core.module';
import { InstanceGroupModule } from '../instance-group/instance-group.module';
import { SharedModule } from '../shared/shared.module';
import { ApplicationConfigurationCardComponent } from './components/application-configuration-card/application-configuration-card.component';
import { ApplicationDescriptorCardComponent } from './components/application-descriptor-card/application-descriptor-card.component';
import { ApplicationDescriptorDetailComponent } from './components/application-descriptor-detail/application-descriptor-detail.component';
import { ApplicationEditCommandPreviewComponent } from './components/application-edit-command-preview/application-edit-command-preview.component';
import { ApplicationEditEndpointsComponent } from './components/application-edit-endpoints/application-edit-endpoints.component';
import { ApplicationEditManualComponent } from './components/application-edit-manual/application-edit-manual.component';
import { ApplicationEditOptionalComponent } from './components/application-edit-optional/application-edit-optional.component';
import { ApplicationEditComponent } from './components/application-edit/application-edit.component';
import { ApplicationTemplateVariableDialogComponent } from './components/application-template-variable-dialog/application-template-variable-dialog.component';
import { ClientInfoComponent } from './components/client-info/client-info.component';
import { CustomEditorComponent } from './components/custom-editor/custom-editor.component';
import { DataFilesBrowserComponent } from './components/data-files-browser/data-files-browser.component';
import { InstanceAddEditComponent } from './components/instance-add-edit/instance-add-edit.component';
import { InstanceBannerEditComponent } from './components/instance-banner-edit/instance-banner-edit.component';
import { InstanceBrowserComponent } from './components/instance-browser/instance-browser.component';
import { InstanceCardComponent } from './components/instance-card/instance-card.component';
import { InstanceEditPortsComponent } from './components/instance-edit-ports/instance-edit-ports.component';
import { InstanceHistoryCompareComponent } from './components/instance-history-compare/instance-history-compare.component';
import { InstanceHistoryTimelineCardComponent } from './components/instance-history-timeline-card/instance-history-timeline-card.component';
import { InstanceHistoryTimelineContentComponent } from './components/instance-history-timeline-content/instance-history-timeline-content.component';
import { InstanceHistoryTimelineComponent } from './components/instance-history-timeline/instance-history-timeline.component';
import { InstanceHistoryComponent } from './components/instance-history/instance-history.component';
import { InstanceNodeCardComponent } from './components/instance-node-card/instance-node-card.component';
import { InstanceNodePortListComponent } from './components/instance-node-port-list/instance-node-port-list.component';
import { InstanceNotificationsItemComponent } from './components/instance-notifications-item/instance-notifications-item.component';
import { InstanceNotificationsComponent } from './components/instance-notifications/instance-notifications.component';
import { InstanceShiftPortsComponent } from './components/instance-shift-ports/instance-shift-ports.component';
import { InstanceSyncComponent } from './components/instance-sync/instance-sync.component';
import { InstanceTemplateGroupDetailComponent } from './components/instance-template-group-detail/instance-template-group-detail.component';
import { InstanceTemplateComponent } from './components/instance-template/instance-template.component';
import { InstanceVersionCardComponent } from './components/instance-version-card/instance-version-card.component';
import { InstanceVersionHistoryCardComponent } from './components/instance-version-history-card/instance-version-history-card.component';
import { ProcessConfigurationComponent } from './components/process-configuration/process-configuration.component';
import { ProcessDetailsComponent } from './components/process-details/process-details.component';
import { ProcessListComponent } from './components/process-list/process-list.component';
import { ProcessPortListComponent } from './components/process-port-list/process-port-list.component';
import { ProcessStartConfirmComponent } from './components/process-start-confirm/process-start-confirm.component';
import { ProcessStatusComponent } from './components/process-status/process-status.component';
import { InstanceRoutingModule } from './instance-routing.module';

@NgModule({
  declarations: [
    InstanceBrowserComponent,
    InstanceCardComponent,
    InstanceAddEditComponent,
    ProcessConfigurationComponent,
    ProcessConfigurationComponent,
    InstanceNodeCardComponent,
    InstanceNodePortListComponent,
    ApplicationDescriptorCardComponent,
    ApplicationConfigurationCardComponent,
    InstanceVersionCardComponent,
    ApplicationEditComponent,
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    ProcessDetailsComponent,
    ProcessStatusComponent,
    ProcessListComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessStartConfirmComponent,
    ClientInfoComponent,
    DataFilesBrowserComponent,
    InstanceVersionHistoryCardComponent,
    InstanceNotificationsComponent,
    InstanceNotificationsItemComponent,
    InstanceSyncComponent,
    ApplicationEditEndpointsComponent,
    CustomEditorComponent,
    InstanceTemplateComponent,
    InstanceTemplateGroupDetailComponent,
    ApplicationDescriptorDetailComponent,
    ApplicationTemplateVariableDialogComponent,
    ProcessPortListComponent,
    InstanceEditPortsComponent,
    InstanceShiftPortsComponent,
    InstanceHistoryComponent,
    InstanceHistoryTimelineComponent,
    InstanceHistoryTimelineCardComponent,
    InstanceHistoryCompareComponent,
    InstanceHistoryTimelineContentComponent,
    InstanceBannerEditComponent,
  ],
  entryComponents: [
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessListComponent,
    ProcessStartConfirmComponent,
    InstanceBannerEditComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    InstanceGroupModule,
    InstanceRoutingModule,
    PortalModule,
    DragulaModule.forRoot(),
  ],
})
export class InstanceModule {}
