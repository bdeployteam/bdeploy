import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { LoadingBarHttpClientModule } from '@ngx-loading-bar/http-client';
import { LoadingBarRouterModule } from '@ngx-loading-bar/router';
import { AceEditorModule } from 'ng2-ace-editor';
import { DragulaModule } from 'ng2-dragula';
import { DeviceDetectorModule } from 'ngx-device-detector';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ApplicationConfigurationCardComponent } from './application-configuration-card/application-configuration-card.component';
import { ApplicationDescriptorCardComponent } from './application-descriptor-card/application-descriptor-card.component';
import { ApplicationEditCommandPreviewComponent } from './application-edit-command-preview/application-edit-command-preview.component';
import { ApplicationEditManualComponent } from './application-edit-manual/application-edit-manual.component';
import { ApplicationEditOptionalComponent } from './application-edit-optional/application-edit-optional.component';
import { ApplicationEditComponent } from './application-edit/application-edit.component';
import { ClientAppsComponent } from './client-apps/client-apps.component';
import { ClientInfoComponent } from './client-info/client-info.component';
import { ConfigFilesBrowserComponent } from './config-files-browser/config-files-browser.component';
import { DataFilesBrowserComponent } from './data-files-browser/data-files-browser.component';
import { InstanceAddEditComponent } from './instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './instance-browser/instance-browser.component';
import { InstanceCardComponent } from './instance-card/instance-card.component';
import { InstanceNodeCardComponent } from './instance-node-card/instance-node-card.component';
import { InstanceVersionCardComponent } from './instance-version-card/instance-version-card.component';
import { InstanceVersionHistoryCardComponent } from './instance-version-history-card/instance-version-history-card.component';
import { CoreModule } from './modules/core/core.module';
import { InstanceGroupModule } from './modules/instance-group/instance-group.module';
import { SharedModule } from './modules/shared/shared.module';
import { ProcessConfigurationComponent } from './process-configuration/process-configuration.component';
import { ProcessDetailsComponent } from './process-details/process-details.component';
import { ProcessListComponent } from './process-list/process-list.component';
import { ProcessStartConfirmComponent } from './process-start-confirm/process-start-confirm.component';
import { ProcessStatusComponent } from './process-status/process-status.component';

@NgModule({
  declarations: [
    AppComponent,
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
    ClientAppsComponent,
    InstanceVersionHistoryCardComponent,
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    CoreModule,
    SharedModule,
    AceEditorModule,
    LoadingBarHttpClientModule,
    LoadingBarRouterModule,
    DeviceDetectorModule.forRoot(),
    DragulaModule.forRoot(),

    // TODO: REMOVE! once all components are modularized and can depend directly.
    InstanceGroupModule
  ],
  entryComponents: [
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessListComponent,
    ProcessStartConfirmComponent,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
