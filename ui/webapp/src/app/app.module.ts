import { LayoutModule } from '@angular/cdk/layout';
import { HttpClientModule } from '@angular/common/http';
import { APP_INITIALIZER, ErrorHandler, NgModule } from '@angular/core';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatBadgeModule, MatBottomSheetModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatChipsModule, MatDialogModule, MatDividerModule, MatExpansionModule, MatFormFieldModule, MatGridListModule, MatIconModule, MatInputModule, MatListModule, MatMenuModule, MatPaginatorModule, MatProgressBarModule, MatProgressSpinnerModule, MatRadioModule, MatRippleModule, MatSelectModule, MatSidenavModule, MatSlideToggleModule, MatSnackBarModule, MatSortModule, MatStepperModule, MatTableModule, MatTabsModule, MatToolbarModule, MatTooltipModule, MatTreeModule } from '@angular/material';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { LoadingBarHttpClientModule } from '@ngx-loading-bar/http-client';
import { LoadingBarRouterModule } from '@ngx-loading-bar/router';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { AceEditorModule } from 'ng2-ace-editor';
import { DragulaModule } from 'ng2-dragula';
import { CookieService } from 'ngx-cookie-service';
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
import { ConnectionLostComponent } from './connection-lost/connection-lost.component';
import { DataFilesBrowserComponent } from './data-files-browser/data-files-browser.component';
import { ClickStopPropagationDirective } from './directives/click-stop-propagation.directive';
import { FileDropDirective } from './directives/file-drop.directive';
import { FileUploadComponent } from './file-upload/file-upload.component';
import { FileViewerComponent } from './file-viewer/file-viewer.component';
import { GlobalErrorHandler } from './global-error-handler';
import { HiveBrowserComponent } from './hive-browser/hive-browser.component';
import { HiveComponent } from './hive/hive.component';
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
import { httpInterceptorProviders } from './interceptors';
import { LoginComponent } from './login/login.component';
import { LogoComponent } from './logo/logo.component';
import { MainNavComponent } from './main-nav/main-nav.component';
import { ManagedServerDetailComponent } from './managed-server-detail/managed-server-detail.component';
import { ManagedServersComponent } from './managed-servers/managed-servers.component';
import { MasterCleanupComponent } from './master-cleanup/master-cleanup.component';
import { MessageboxComponent } from './messagebox/messagebox.component';
import { MetricsOverviewComponent } from './metrics-overview/metrics-overview.component';
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
import { RemoteProgressElementComponent } from './remote-progress-element/remote-progress-element.component';
import { RemoteProgressComponent } from './remote-progress/remote-progress.component';
import { ConfigService } from './services/config.service';
import { LoggingService } from './services/logging.service';
import { SoftwareCardComponent } from './software-card/software-card.component';
import { SoftwareListComponent } from './software-list/software-list.component';
import { SoftwareRepoAddEditComponent } from './software-repo-add-edit/software-repo-add-edit.component';
import { SoftwareRepositoriesBrowserComponent } from './software-repositories-browser/software-repositories-browser.component';
import { SoftwareRepositoryCardComponent } from './software-repository-card/software-repository-card.component';
import { SoftwareRepositoryComponent } from './software-repository/software-repository.component';
import { ThemeChooserComponent } from './theme-chooser/theme-chooser.component';
import { UpdateBrowserComponent } from './update-browser/update-browser.component';
import { UpdateCardComponent } from './update-card/update-card.component';
import { UpdateDialogComponent } from './update-dialog/update-dialog.component';

export function loadAppConfig(cfgService: ConfigService) {
  return () => cfgService.load();
}

@NgModule({
  declarations: [
    AppComponent,
    MainNavComponent,
    LoginComponent,
    ProductsComponent,
    HiveComponent,
    HiveBrowserComponent,
    InstanceGroupLogoComponent,
    InstanceGroupBrowserComponent,
    InstanceGroupCardComponent,
    InstanceGroupAddEditComponent,
    InstanceGroupDeleteDialogComponent,
    InstanceBrowserComponent,
    InstanceCardComponent,
    InstanceAddEditComponent,
    ProcessConfigurationComponent,
    ClickStopPropagationDirective,
    ProcessConfigurationComponent,
    ClickStopPropagationDirective,
    InstanceGroupLogoComponent,
    InstanceNodeCardComponent,
    ApplicationDescriptorCardComponent,
    ApplicationConfigurationCardComponent,
    MessageboxComponent,
    ProductCardComponent,
    ProductListComponent,
    FileDropDirective,
    InstanceVersionCardComponent,
    ApplicationEditComponent,
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    RemoteProgressComponent,
    RemoteProgressElementComponent,
    ProductTagCardComponent,
    LogoComponent,
    ThemeChooserComponent,
    ProcessDetailsComponent,
    FileUploadComponent,
    SoftwareRepositoriesBrowserComponent,
    SoftwareRepositoryComponent,
    SoftwareRepositoryCardComponent,
    SoftwareRepoAddEditComponent,
    SoftwareCardComponent,
    SoftwareListComponent,
    ProcessStatusComponent,
    ProcessListComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessStartConfirmComponent,
    ClientInfoComponent,
    ConfigFilesBrowserComponent,
    UpdateBrowserComponent,
    UpdateCardComponent,
    UpdateDialogComponent,
    MasterCleanupComponent,
    FileViewerComponent,
    DataFilesBrowserComponent,
    ClientAppsComponent,
    ProductInfoCardComponent,
    InstanceVersionHistoryCardComponent,
    ConnectionLostComponent,
    AttachCentralComponent,
    AttachManagedComponent,
    ManagedServersComponent,
    ManagedServerDetailComponent,
    MetricsOverviewComponent,
    ProductSyncComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    MatButtonModule,
    LayoutModule,
    MatToolbarModule,
    MatSidenavModule,
    MatIconModule,
    MatListModule,
    MatSnackBarModule,
    MatGridListModule,
    MatCardModule,
    MatMenuModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatChipsModule,
    MatDialogModule,
    MatDividerModule,
    MatAutocompleteModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatCheckboxModule,
    MatRippleModule,
    HttpClientModule,
    LoadingBarHttpClientModule,
    LoadingBarRouterModule,
    FormsModule,
    ReactiveFormsModule,
    FlexLayoutModule,
    MatExpansionModule,
    MatSlideToggleModule,
    MatBadgeModule,
    MatBottomSheetModule,
    MatTreeModule,
    MatPaginatorModule,
    MatSortModule,
    AceEditorModule,
    MatTabsModule,
    MatStepperModule,
    MatRadioModule,
    NgxChartsModule,
    DeviceDetectorModule.forRoot(),
    DragulaModule.forRoot()
  ],
  providers: [
    ConfigService,
    LoggingService,
    httpInterceptorProviders,
    CookieService,
    /* make sure that ConfigService and HistoryService are initialize always on startup */
    { provide: APP_INITIALIZER, useFactory: loadAppConfig, deps: [ConfigService], multi: true },
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
  ],
  entryComponents: [
    InstanceGroupDeleteDialogComponent,
    MessageboxComponent,
    ConnectionLostComponent,
    FileUploadComponent,
    ApplicationEditManualComponent,
    ApplicationEditOptionalComponent,
    ApplicationEditCommandPreviewComponent,
    ProcessListComponent,
    ProcessStartConfirmComponent,
    UpdateDialogComponent,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
