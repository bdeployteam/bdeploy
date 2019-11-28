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
import { AttachCentralComponent } from './attach-central/attach-central.component';
import { AttachManagedComponent } from './attach-managed/attach-managed.component';
import { ClientAppsComponent } from './client-apps/client-apps.component';
import { ClientInfoComponent } from './client-info/client-info.component';
import { ConfigFilesBrowserComponent } from './config-files-browser/config-files-browser.component';
import { DataFilesBrowserComponent } from './data-files-browser/data-files-browser.component';
import { InstanceAddEditComponent } from './instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './instance-browser/instance-browser.component';
import { InstanceCardComponent } from './instance-card/instance-card.component';
import { InstanceGroupAddEditComponent } from './instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './instance-group-browser/instance-group-browser.component';
import { InstanceGroupCardComponent } from './instance-group-card/instance-group-card.component';
import { InstanceGroupDeleteDialogComponent } from './instance-group-delete-dialog/instance-group-delete-dialog.component';
import { InstanceGroupLogoComponent } from './instance-group-logo/instance-group-logo.component';
import { InstanceNodeCardComponent } from './instance-node-card/instance-node-card.component';
import { InstanceVersionCardComponent } from './instance-version-card/instance-version-card.component';
import { InstanceVersionHistoryCardComponent } from './instance-version-history-card/instance-version-history-card.component';
import { ManagedServerDetailComponent } from './managed-server-detail/managed-server-detail.component';
import { ManagedServersComponent } from './managed-servers/managed-servers.component';
import { CoreModule } from './modules/core/core.module';
import { SharedModule } from './modules/shared/shared.module';
import { ProcessConfigurationComponent } from './process-configuration/process-configuration.component';
import { ProcessDetailsComponent } from './process-details/process-details.component';
import { ProcessListComponent } from './process-list/process-list.component';
import { ProcessStartConfirmComponent } from './process-start-confirm/process-start-confirm.component';
import { ProcessStatusComponent } from './process-status/process-status.component';
import { ProductCardComponent } from './product-card/product-card.component';
import { ProductInfoCardComponent } from './product-info-card/product-info-card.component';
import { ProductListComponent } from './product-list/product-list.component';
import { ProductSyncComponent } from './product-sync/product-sync.component';
import { ProductTagCardComponent } from './product-tag-card/product-tag-card.component';
import { ProductsComponent } from './products/products.component';

@NgModule({
  declarations: [
    AppComponent,
    ProductsComponent,
    InstanceGroupLogoComponent,
    InstanceGroupBrowserComponent,
    InstanceGroupCardComponent,
    InstanceGroupAddEditComponent,
    InstanceGroupDeleteDialogComponent,
    InstanceBrowserComponent,
    InstanceCardComponent,
    InstanceAddEditComponent,
    ProcessConfigurationComponent,
    ProcessConfigurationComponent,
    InstanceGroupLogoComponent,
    InstanceNodeCardComponent,
    ApplicationDescriptorCardComponent,
    ApplicationConfigurationCardComponent,
    ProductCardComponent,
    ProductListComponent,
    InstanceVersionCardComponent,
    ApplicationEditComponent,
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    ProductTagCardComponent,
    ProcessDetailsComponent,
    ProcessStatusComponent,
    ProcessListComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessStartConfirmComponent,
    ClientInfoComponent,
    ConfigFilesBrowserComponent,
    DataFilesBrowserComponent,
    ClientAppsComponent,
    ProductInfoCardComponent,
    InstanceVersionHistoryCardComponent,
    AttachCentralComponent,
    AttachManagedComponent,
    ManagedServersComponent,
    ManagedServerDetailComponent,
    ProductSyncComponent
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
    DragulaModule.forRoot()
  ],
  entryComponents: [
    InstanceGroupDeleteDialogComponent,
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessListComponent,
    ProcessStartConfirmComponent,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
