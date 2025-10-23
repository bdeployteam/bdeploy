import { Component, inject, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from '../../../../core/services/settings.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatTab, MatTabChangeEvent, MatTabContent, MatTabGroup, MatTabLabel } from '@angular/material/tabs';
import { MatIcon } from '@angular/material/icon';
import { GeneralTabComponent } from './general-tab/general-tab.component';
import { MailSendingTabComponent } from './mail-sending-tab/mail-sending-tab.component';
import { MailReceivingTabComponent } from './mail-receiving-tab/mail-receiving-tab.component';
import { OidcTabComponent } from './oidc-tab/oidc-tab.component';
import { Auth0TabComponent } from './auth0-tab/auth0-tab.component';
import { OktaTabComponent } from './okta-tab/okta-tab.component';
import { LdapTabComponent } from './ldap-tab/ldap-tab.component';
import { AttributesTabComponent } from './attributes-tab/attributes-tab.component';
import { PluginsTabComponent } from './plugins-tab/plugins-tab.component';

@Component({
  selector: 'app-settings-general',
  templateUrl: './settings-general.component.html',
  encapsulation: ViewEncapsulation.None,
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdPanelButtonComponent,
    MatDivider,
    BdButtonComponent,
    BdDialogContentComponent,
    MatTabGroup,
    MatTab,
    MatTabLabel,
    MatIcon,
    GeneralTabComponent,
    MailSendingTabComponent,
    MailReceivingTabComponent,
    OidcTabComponent,
    Auth0TabComponent,
    OktaTabComponent,
    LdapTabComponent,
    AttributesTabComponent,
    MatTabContent,
    PluginsTabComponent,
  ],
})
export class SettingsGeneralComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly areas = inject(NavAreasService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly settings = inject(SettingsService);

  protected addPlugin$ = new Subject<unknown>();
  protected tabIndex: number;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  ngOnInit() {
    this.areas.registerDirtyable(this, 'admin');
    this.tabIndex = Number.parseInt(this.route.snapshot.queryParamMap.get('tabIndex'), 10);
  }

  ngOnDestroy(): void {
    this.settings.discard();
  }

  public isDirty(): boolean {
    return this.settings.isDirty();
  }

  public doSave(): Observable<unknown> {
    return this.settings.save();
  }

  protected tabChanged(tab: MatTabChangeEvent) {
    this.router.navigate(['', { outlets: { panel: null } }], { queryParams: { tabIndex: tab.index } });
    this.tabIndex = tab.index;
  }
}
