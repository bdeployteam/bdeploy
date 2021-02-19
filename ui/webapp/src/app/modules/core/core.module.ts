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
import { BdButtonPopupComponent } from './components/bd-button-popup/bd-button-popup.component';
import { BdButtonComponent } from './components/bd-button/bd-button.component';
import { BdDataCardComponent } from './components/bd-data-card/bd-data-card.component';
import { BdDataDisplayComponent } from './components/bd-data-display/bd-data-display.component';
import { BdDataGridComponent } from './components/bd-data-grid/bd-data-grid.component';
import { BdDataGroupingComponent } from './components/bd-data-grouping/bd-data-grouping.component';
import { BdDataTableComponent } from './components/bd-data-table/bd-data-table.component';
import { ConnectionLostComponent } from './components/connection-lost/connection-lost.component';
import { LoginComponent } from './components/login/login.component';
import { LogoComponent } from './components/logo/logo.component';
import { MainNavContentComponent } from './components/main-nav-content/main-nav-content.component';
import { MainNavFlyinComponent } from './components/main-nav-flyin/main-nav-flyin.component';
import { MainNavMenuComponent } from './components/main-nav-menu/main-nav-menu.component';
import { MainNavTopComponent } from './components/main-nav-top/main-nav-top.component';
import { MainNavComponent } from './components/main-nav/main-nav.component';
import { MessageboxComponent } from './components/messagebox/messagebox.component';
import { SearchFieldComponent } from './components/search-field/search-field.component';
import { ThemeChooserComponent } from './components/theme-chooser/theme-chooser.component';
import { UserAvatarComponent } from './components/user-avatar/user-avatar.component';
import { UserEditComponent } from './components/user-edit/user-edit.component';
import { UserInfoComponent } from './components/user-info/user-info.component';
import { UserPasswordComponent } from './components/user-password/user-password.component';
import { UserPickerComponent } from './components/user-picker/user-picker.component';
import { ClickStopPropagationDirective } from './directives/click-stop-propagation.directive';
import { FileDropDirective } from './directives/file-drop.directive';
import { httpInterceptorProviders } from './interceptors';
import { VersionPipe } from './pipes/version.pipe';
import { ConfigService } from './services/config.service';
import { BdDataGroupingPanelComponent } from './components/bd-data-grouping-panel/bd-data-grouping-panel.component';

export function loadAppConfig(cfgService: ConfigService) {
  return () => cfgService.load();
}

@NgModule({
  declarations: [
    MainNavComponent,
    LogoComponent,
    ThemeChooserComponent,
    FileDropDirective,
    ClickStopPropagationDirective,
    ConnectionLostComponent,
    LoginComponent,
    UserInfoComponent,
    UserAvatarComponent,
    UserEditComponent,
    UserPasswordComponent,
    VersionPipe,
    UserPickerComponent,
    MainNavTopComponent,
    MainNavFlyinComponent,
    MainNavMenuComponent,
    MainNavContentComponent,
    SearchFieldComponent,
    BdButtonComponent,
    MessageboxComponent,
    BdDataTableComponent,
    BdDataGridComponent,
    BdDataCardComponent,
    BdDataDisplayComponent,
    BdButtonPopupComponent,
    BdDataGroupingComponent,
    BdDataGroupingPanelComponent,
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

    // our own core components
    MainNavComponent,
    LogoComponent,
    ThemeChooserComponent,
    FileDropDirective,
    ClickStopPropagationDirective,
    LoginComponent,
    UserAvatarComponent,
    VersionPipe,
    MessageboxComponent,
    BdButtonComponent,
    BdDataTableComponent,
    BdDataGridComponent,
    BdDataDisplayComponent,
    BdButtonPopupComponent,
    BdDataGroupingComponent,
  ],
})
export class CoreModule {}
