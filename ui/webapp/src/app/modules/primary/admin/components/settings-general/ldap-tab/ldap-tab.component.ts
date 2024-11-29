import { moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import {
  BdDataTableComponent,
  DragReorderEvent,
} from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { LdapCheckActionComponent } from './ldap-check-action/ldap-check-action.component';
import { LdapEditActionComponent } from './ldap-edit-action/ldap-edit-action.component';
import { LdapImportActionComponent } from './ldap-import-action/ldap-import-action.component';

@Component({
    selector: 'app-ldap-tab',
    templateUrl: './ldap-tab.component.html',
    standalone: false
})
export class LdapTabComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  protected readonly settings = inject(SettingsService);

  private readonly colServer: BdDataColumn<LDAPSettingsDto> = {
    id: 'server',
    name: 'Server',
    data: (r) => r.server,
    isId: true,
  };

  private readonly colDescription: BdDataColumn<LDAPSettingsDto> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.description,
  };

  private readonly colCheck: BdDataColumn<LDAPSettingsDto> = {
    id: 'check',
    name: 'Check',
    data: (r) => `Check connection to ${r.server}`,
    component: LdapCheckActionComponent,
    width: '40px',
    actionDisabled: (r) => this.isEditMode(r),
  };

  private readonly colImport: BdDataColumn<LDAPSettingsDto> = {
    id: 'import',
    name: 'Import',
    data: (r) => `Import accounts from ${r.server}`,
    component: LdapImportActionComponent,
    width: '40px',
    actionDisabled: (r) => this.isEditMode(r),
  };

  private readonly colEdit: BdDataColumn<LDAPSettingsDto> = {
    id: 'edit',
    name: 'Edit',
    data: (r) => `Edit server ${r.server}`,
    component: LdapEditActionComponent,
    icon: () => 'edit',
    width: '40px',
  };

  private readonly colDelete: BdDataColumn<LDAPSettingsDto> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove server ${r.server}`,
    action: (r) => this.settings.removeLdapServer(r),
    icon: () => 'delete',
    width: '40px',
    actionDisabled: (r) => this.isEditMode(r),
  };

  protected readonly columns: BdDataColumn<LDAPSettingsDto>[] = [
    this.colServer,
    this.colDescription,
    this.colCheck,
    this.colImport,
    this.colEdit,
    this.colDelete,
  ];
  protected tempServer: Partial<LDAPSettingsDto>;
  protected checkResult$ = new Subject<string>();
  private selectedServerId: string;

  private subscription: Subscription;

  @ViewChild(BdDataTableComponent) private readonly table: BdDataTableComponent<LDAPSettingsDto>;

  ngOnInit(): void {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      if (!route?.params?.['id']) {
        this.selectedServerId = null;
        return;
      }
      this.selectedServerId = route.params['id'];
    });
    this.subscription.add(this.settings.settingsUpdated$.subscribe(() => this.table?.update()));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onReorder(order: DragReorderEvent<LDAPSettingsDto>) {
    if (order.previousIndex === order.currentIndex) {
      return;
    }

    moveItemInArray(this.settings.settings$.value.auth.ldapSettings, order.previousIndex, order.currentIndex);
    this.settings.serversReordered();
  }

  private isEditMode(r) {
    return this.selectedServerId === r.id;
  }
}
