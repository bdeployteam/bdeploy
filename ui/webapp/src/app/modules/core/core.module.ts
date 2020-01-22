import { LayoutModule } from '@angular/cdk/layout';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { APP_INITIALIZER, ErrorHandler, NgModule } from '@angular/core';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatBadgeModule, MatBottomSheetModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatChipsModule, MatDialogModule, MatDividerModule, MatExpansionModule, MatFormFieldModule, MatGridListModule, MatIconModule, MatInputModule, MatListModule, MatMenuModule, MatPaginatorModule, MatProgressBarModule, MatProgressSpinnerModule, MatRadioModule, MatRippleModule, MatSelectModule, MatSidenavModule, MatSliderModule, MatSlideToggleModule, MatSnackBarModule, MatSortModule, MatStepperModule, MatTableModule, MatTabsModule, MatToolbarModule, MatTooltipModule, MatTreeModule } from '@angular/material';
import { RouterModule } from '@angular/router';
import { ClipboardModule } from 'ngx-clipboard';
import { CookieService } from 'ngx-cookie-service';
import { GravatarModule } from 'ngx-gravatar';
import { GlobalErrorHandler } from 'src/app/modules/core/global-error-handler';
import { ConnectionLostComponent } from './components/connection-lost/connection-lost.component';
import { InstanceGroupCardComponent } from './components/instance-group-card/instance-group-card.component';
import { InstanceGroupLogoComponent } from './components/instance-group-logo/instance-group-logo.component';
import { LoginComponent } from './components/login/login.component';
import { LogoComponent } from './components/logo/logo.component';
import { MainNavComponent } from './components/main-nav/main-nav.component';
import { ManagedServerUpdateComponent } from './components/managed-server-update/managed-server-update.component';
import { ProductInfoCardComponent } from './components/product-info-card/product-info-card.component';
import { ProductTagCardComponent } from './components/product-tag-card/product-tag-card.component';
import { ThemeChooserComponent } from './components/theme-chooser/theme-chooser.component';
import { UserAvatarComponent } from './components/user-avatar/user-avatar.component';
import { UserEditComponent } from './components/user-edit/user-edit.component';
import { UserInfoComponent } from './components/user-info/user-info.component';
import { UserPasswordComponent } from './components/user-password/user-password.component';
import { ClickStopPropagationDirective } from './directives/click-stop-propagation.directive';
import { FileDropDirective } from './directives/file-drop.directive';
import { httpInterceptorProviders } from './interceptors';
import { ConfigService } from './services/config.service';

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
    ManagedServerUpdateComponent,
    InstanceGroupLogoComponent,
    InstanceGroupCardComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
    UserInfoComponent,
    UserAvatarComponent,
    UserEditComponent,
    UserPasswordComponent,
  ],
  entryComponents: [
    ConnectionLostComponent,
    UserEditComponent,
    UserPasswordComponent,
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
    ManagedServerUpdateComponent,
    InstanceGroupLogoComponent,
    InstanceGroupCardComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
    UserAvatarComponent,
  ]
})
export class CoreModule { }
