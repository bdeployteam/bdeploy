import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { NgTerminalModule } from 'ng-terminal';
import { MonacoEditorModule } from 'ngx-monaco-editor';
import { CoreModule } from '../../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { BHiveComponent } from './components/bhive/bhive.component';
import { LogFilesBrowserComponent } from './components/log-files-browser/log-files-browser.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { AttributesTabComponent } from './components/settings-general/attributes-tab/attributes-tab.component';
import { AuthTestComponent } from './components/settings-general/auth-test/auth-test.component';
import { GeneralTabComponent } from './components/settings-general/general-tab/general-tab.component';
import { LdapTabComponent } from './components/settings-general/ldap-tab/ldap-tab.component';
import { PluginDeleteActionComponent } from './components/settings-general/plugins-tab/plugin-delete-action/plugin-delete-action.component';
import { PluginLoadActionComponent } from './components/settings-general/plugins-tab/plugin-load-action/plugin-load-action.component';
import { PluginsTabComponent } from './components/settings-general/plugins-tab/plugins-tab.component';
import { SettingsGeneralComponent } from './components/settings-general/settings-general.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';
import { UsersBrowserComponent } from './components/users-browser/users-browser.component';

@NgModule({
  declarations: [
    LogFilesBrowserComponent,
    UpdateBrowserComponent,
    MasterCleanupComponent,
    MetricsOverviewComponent,
    AdminShellComponent,
    SettingsGeneralComponent,
    UsersBrowserComponent,
    UsersBrowserComponent,
    GeneralTabComponent,
    AttributesTabComponent,
    PluginsTabComponent,
    PluginLoadActionComponent,
    PluginDeleteActionComponent,
    LdapTabComponent,
    AuthTestComponent,
    BHiveComponent,
  ],
  imports: [
    CommonModule,
    CoreModule,
    AdminRoutingModule,
    NgxChartsModule,

    MatDialogModule,
    FormsModule,
    ReactiveFormsModule,

    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTabsModule,
    MatSidenavModule,
    MatPaginatorModule,
    MatSortModule,
    MatMenuModule,
    MatChipsModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
    MatProgressBarModule,
    MatCardModule,
    MatListModule,

    NgTerminalModule,
    MonacoEditorModule.forRoot(),
  ],
})
export class AdminModule {}
