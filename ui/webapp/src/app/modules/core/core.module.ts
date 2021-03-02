import { LayoutModule } from '@angular/cdk/layout';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { APP_INITIALIZER, ErrorHandler, NgModule } from '@angular/core';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatBadgeModule } from '@angular/material/badge';
import { MatBottomSheetModule } from '@angular/material/bottom-sheet';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatRippleModule } from '@angular/material/core';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTreeModule } from '@angular/material/tree';
import { RouterModule } from '@angular/router';
import { ClipboardModule } from 'ngx-clipboard';
import { CookieService } from 'ngx-cookie-service';
import { GravatarModule } from 'ngx-gravatar';
import { GlobalErrorHandler } from 'src/app/modules/core/global-error-handler';
import { BdActionRowComponent } from './components/bd-action-row/bd-action-row.component';
import { BdButtonPopupComponent } from './components/bd-button-popup/bd-button-popup.component';
import { BdButtonComponent } from './components/bd-button/bd-button.component';
import { BdDataCardComponent } from './components/bd-data-card/bd-data-card.component';
import { BdDataComponentCellComponent } from './components/bd-data-component-cell/bd-data-component-cell.component';
import { BdDataDisplayComponent } from './components/bd-data-display/bd-data-display.component';
import { BdDataGridComponent } from './components/bd-data-grid/bd-data-grid.component';
import { BdDataGroupingPanelComponent } from './components/bd-data-grouping-panel/bd-data-grouping-panel.component';
import { BdDataGroupingComponent } from './components/bd-data-grouping/bd-data-grouping.component';
import { BdDataTableComponent } from './components/bd-data-table/bd-data-table.component';
import { BdDialogContentComponent } from './components/bd-dialog-content/bd-dialog-content.component';
import { BdDialogToolbarComponent } from './components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from './components/bd-dialog/bd-dialog.component';
import {
  BdDynamicComponent,
  DYNAMIC_BASE_MODULES as DYNAMIC_BASE_MODULES,
} from './components/bd-dynamic/bd-dynamic.component';
import { BdFileDropComponent } from './components/bd-file-drop/bd-file-drop.component';
import { BdFileUploadComponent } from './components/bd-file-upload/bd-file-upload.component';
import { BdFormInputComponent } from './components/bd-form-input/bd-form-input.component';
import { BdFormSelectComponent } from './components/bd-form-select/bd-form-select.component';
import { BdFormToggleComponent } from './components/bd-form-toggle/bd-form-toggle.component';
import { BdImageUploadComponent } from './components/bd-image-upload/bd-image-upload.component';
import { BdLoadingOverlayComponent } from './components/bd-loading-overlay/bd-loading-overlay.component';
import { BdLogoComponent } from './components/bd-logo/bd-logo.component';
import { BdMicroIconButtonComponent } from './components/bd-micro-icon-button/bd-micro-icon-button.component';
import { BdNoDataComponent } from './components/bd-no-data/bd-no-data.component';
import { BdNotificationCardComponent } from './components/bd-notification-card/bd-notification-card.component';
import { BdPanelToggleButtonComponent } from './components/bd-panel-button/bd-panel-button.component';
import { BdSearchFieldComponent } from './components/bd-search-field/bd-search-field.component';
import { ConnectionLostComponent } from './components/connection-lost/connection-lost.component';
import { LoginComponent } from './components/login/login.component';
import { MainNavButtonComponent } from './components/main-nav-button/main-nav-button.component';
import { MainNavContentComponent } from './components/main-nav-content/main-nav-content.component';
import { MainNavFlyinComponent } from './components/main-nav-flyin/main-nav-flyin.component';
import { MainNavMenuComponent } from './components/main-nav-menu/main-nav-menu.component';
import { MainNavTopComponent } from './components/main-nav-top/main-nav-top.component';
import { MainNavComponent } from './components/main-nav/main-nav.component';
import { MessageboxComponent } from './components/messagebox/messagebox.component';
import { UserAvatarComponent } from './components/user-avatar/user-avatar.component';
import { UserEditComponent } from './components/user-edit/user-edit.component';
import { UserInfoComponent } from './components/user-info/user-info.component';
import { UserPasswordComponent } from './components/user-password/user-password.component';
import { UserPickerComponent } from './components/user-picker/user-picker.component';
import { ClickStopPropagationDirective } from './directives/click-stop-propagation.directive';
import { FileDropDirective } from './directives/file-drop.directive';
import { httpInterceptorProviders } from './interceptors';
import { SafeHtmlPipe } from './pipes/safeHtml.pipe';
import { VersionPipe } from './pipes/version.pipe';
import { ConfigService } from './services/config.service';
import { GroupIdValidator } from './validators/group-id';
import { BdDialogMessageComponent } from './components/bd-dialog-message/bd-dialog-message.component';

export function loadAppConfig(cfgService: ConfigService) {
  return () => cfgService.load();
}

@NgModule({
  declarations: [
    MainNavComponent,
    BdLogoComponent,
    FileDropDirective,
    ClickStopPropagationDirective,
    ConnectionLostComponent,
    LoginComponent,
    UserInfoComponent,
    UserAvatarComponent,
    UserEditComponent,
    UserPasswordComponent,
    VersionPipe,
    SafeHtmlPipe,
    UserPickerComponent,
    MainNavTopComponent,
    MainNavFlyinComponent,
    MainNavMenuComponent,
    MainNavContentComponent,
    BdSearchFieldComponent,
    BdButtonComponent,
    MessageboxComponent,
    BdDataTableComponent,
    BdDataGridComponent,
    BdDataCardComponent,
    BdDataDisplayComponent,
    BdButtonPopupComponent,
    BdDataGroupingComponent,
    BdDataGroupingPanelComponent,
    BdLoadingOverlayComponent,
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    MainNavButtonComponent,
    BdNoDataComponent,
    BdPanelToggleButtonComponent,
    BdImageUploadComponent,
    BdActionRowComponent,
    BdFormInputComponent,
    GroupIdValidator,
    BdFormToggleComponent,
    BdFormSelectComponent,
    BdDataComponentCellComponent,
    BdDynamicComponent,
    BdFileDropComponent,
    BdFileUploadComponent,
    BdMicroIconButtonComponent,
    BdNotificationCardComponent,
    BdDialogMessageComponent,
  ],
  entryComponents: [
    ConnectionLostComponent,
    UserEditComponent,
    UserPasswordComponent,
    UserPickerComponent,
    MessageboxComponent,
  ],
  providers: [
    httpInterceptorProviders,
    CookieService,
    /* make sure that ConfigService and HistoryService are initialize always on startup */
    { provide: APP_INITIALIZER, useFactory: loadAppConfig, deps: [ConfigService], multi: true },
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    { provide: DYNAMIC_BASE_MODULES, useValue: [CoreModule] },
  ],
  imports: [
    CommonModule,
    MatButtonModule,
    MatButtonToggleModule,
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
    MatExpansionModule,
    MatSlideToggleModule,
    MatBadgeModule,
    MatBottomSheetModule,
    MatTreeModule,
    MatPaginatorModule,
    MatSortModule,
    MatTabsModule,
    MatStepperModule,
    MatRadioModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    FlexLayoutModule,
    RouterModule,
    LayoutModule,
    GravatarModule,
    ClipboardModule,
  ],
  exports: [
    // FIXME: go through and remove not required exports - other modules should MOSTLY use BD framework now.
    MatButtonModule,
    MatButtonToggleModule,
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
    MatExpansionModule,
    MatSlideToggleModule,
    MatSliderModule,
    MatBadgeModule,
    MatBottomSheetModule,
    MatTreeModule,
    MatPaginatorModule,
    MatSortModule,
    MatTabsModule,
    MatStepperModule,
    MatRadioModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    FlexLayoutModule,
    LayoutModule,

    // our own exported components - names need to be aligned.
    MainNavComponent,
    BdLogoComponent, // FIXME: Remove
    FileDropDirective,
    ClickStopPropagationDirective,
    LoginComponent,
    UserAvatarComponent,
    VersionPipe,
    MessageboxComponent,
    BdDynamicComponent,

    // framework components to be used by others
    BdButtonComponent,
    BdDataTableComponent,
    BdDataGridComponent,
    BdDataDisplayComponent,
    BdButtonPopupComponent,
    BdDataGroupingComponent,
    BdLoadingOverlayComponent,
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    BdNoDataComponent,
    BdPanelToggleButtonComponent,
    BdImageUploadComponent,
    BdActionRowComponent,
    BdFormInputComponent,
    BdFormToggleComponent,
    BdFormSelectComponent,
    BdFileDropComponent,
    BdFileUploadComponent,
    BdMicroIconButtonComponent,
    BdNotificationCardComponent,

    // validators
    GroupIdValidator,
  ],
})
export class CoreModule {}
