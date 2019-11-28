import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { AceEditorModule } from 'ng2-ace-editor';
import { DragulaModule } from 'ng2-dragula';
import { CoreModule } from '../core/core.module';
import { InstanceGroupModule } from '../instance-group/instance-group.module';
import { SharedModule } from '../shared/shared.module';
import { ApplicationConfigurationCardComponent } from './components/application-configuration-card/application-configuration-card.component';
import { ApplicationDescriptorCardComponent } from './components/application-descriptor-card/application-descriptor-card.component';
import { ApplicationEditCommandPreviewComponent } from './components/application-edit-command-preview/application-edit-command-preview.component';
import { ApplicationEditManualComponent } from './components/application-edit-manual/application-edit-manual.component';
import { ApplicationEditOptionalComponent } from './components/application-edit-optional/application-edit-optional.component';
import { ApplicationEditComponent } from './components/application-edit/application-edit.component';
import { ClientInfoComponent } from './components/client-info/client-info.component';
import { ConfigFilesBrowserComponent } from './components/config-files-browser/config-files-browser.component';
import { DataFilesBrowserComponent } from './components/data-files-browser/data-files-browser.component';
import { InstanceAddEditComponent } from './components/instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './components/instance-browser/instance-browser.component';
import { InstanceCardComponent } from './components/instance-card/instance-card.component';
import { InstanceNodeCardComponent } from './components/instance-node-card/instance-node-card.component';
import { InstanceVersionCardComponent } from './components/instance-version-card/instance-version-card.component';
import { InstanceVersionHistoryCardComponent } from './components/instance-version-history-card/instance-version-history-card.component';
import { ProcessConfigurationComponent } from './components/process-configuration/process-configuration.component';
import { ProcessDetailsComponent } from './components/process-details/process-details.component';
import { ProcessListComponent } from './components/process-list/process-list.component';
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
    ConfigFilesBrowserComponent,
    DataFilesBrowserComponent,
    InstanceVersionHistoryCardComponent,
  ],
  entryComponents: [
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessListComponent,
    ProcessStartConfirmComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    InstanceGroupModule,
    InstanceRoutingModule,

    AceEditorModule,
    DragulaModule.forRoot(),
  ]
})
export class InstanceModule { }
