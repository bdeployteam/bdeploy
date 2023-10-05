import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSortModule } from '@angular/material/sort';
import { MatTabsModule } from '@angular/material/tabs';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { NgTerminalModule } from 'ng-terminal';
import { MonacoEditorModule } from 'ngx-monaco-editor';
import { CoreModule } from '../../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { BHiveComponent } from './components/bhive/bhive.component';
import { LogFilesBrowserComponent } from './components/log-files-browser/log-files-browser.component';
import { ManualJobsComponent } from './components/manual-jobs/manual-jobs.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { NodesComponent } from './components/nodes/nodes.component';
import { AttributeEditActionComponent } from './components/settings-general/attributes-tab/attribute-edit-action/attribute-edit-action.component';
import { AttributesTabComponent } from './components/settings-general/attributes-tab/attributes-tab.component';
import { Auth0TabComponent } from './components/settings-general/auth0-tab/auth0-tab.component';
import { GeneralTabComponent } from './components/settings-general/general-tab/general-tab.component';
import { LdapCheckActionComponent } from './components/settings-general/ldap-tab/ldap-check-action/ldap-check-action.component';
import { LdapEditActionComponent } from './components/settings-general/ldap-tab/ldap-edit-action/ldap-edit-action.component';
import { LdapImportActionComponent } from './components/settings-general/ldap-tab/ldap-import-action/ldap-import-action.component';
import { LdapTabComponent } from './components/settings-general/ldap-tab/ldap-tab.component';
import { MailReceivingTabComponent } from './components/settings-general/mail-receiving-tab/mail-receiving-tab.component';
import { MailSendingTabComponent } from './components/settings-general/mail-sending-tab/mail-sending-tab.component';
import { OidcTabComponent } from './components/settings-general/oidc-tab/oidc-tab.component';
import { OktaTabComponent } from './components/settings-general/okta-tab/okta-tab.component';
import { PluginDeleteActionComponent } from './components/settings-general/plugins-tab/plugin-delete-action/plugin-delete-action.component';
import { PluginLoadActionComponent } from './components/settings-general/plugins-tab/plugin-load-action/plugin-load-action.component';
import { PluginsTabComponent } from './components/settings-general/plugins-tab/plugins-tab.component';
import { SettingsGeneralComponent } from './components/settings-general/settings-general.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';
import { UserGroupsBrowserComponent } from './components/user-groups-browser/user-groups-browser.component';
import { UsersBrowserComponent } from './components/users-browser/users-browser.component';

@NgModule({
  declarations: [
    LogFilesBrowserComponent,
    UpdateBrowserComponent,
    MasterCleanupComponent,
    ManualJobsComponent,
    MetricsOverviewComponent,
    AdminShellComponent,
    SettingsGeneralComponent,
    UserGroupsBrowserComponent,
    UsersBrowserComponent,
    GeneralTabComponent,
    AttributesTabComponent,
    PluginsTabComponent,
    PluginLoadActionComponent,
    PluginDeleteActionComponent,
    LdapTabComponent,
    BHiveComponent,
    AttributeEditActionComponent,
    LdapEditActionComponent,
    LdapCheckActionComponent,
    LdapImportActionComponent,
    NodesComponent,
    OidcTabComponent,
    Auth0TabComponent,
    OktaTabComponent,
    MailSendingTabComponent,
    MailReceivingTabComponent,
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
    MatTabsModule,
    MatSidenavModule,
    MatSortModule,
    MatCardModule,
    MatListModule,

    NgTerminalModule,
    MonacoEditorModule.forRoot(),
  ],
})
export class AdminModule {}
