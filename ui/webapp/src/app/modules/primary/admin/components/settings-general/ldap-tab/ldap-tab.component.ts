import { moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { DragReorderEvent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { LdapCheckActionComponent } from './ldap-check-action/ldap-check-action.component';
import { LdapEditActionComponent } from './ldap-edit-action/ldap-edit-action.component';

@Component({
  selector: 'app-ldap-tab',
  templateUrl: './ldap-tab.component.html',
  styleUrls: ['./ldap-tab.component.css'],
})
export class LdapTabComponent implements OnInit, OnDestroy {
  private colServer: BdDataColumn<LDAPSettingsDto> = {
    id: 'server',
    name: 'Server',
    data: (r) => r.server,
  };

  private colDescription: BdDataColumn<LDAPSettingsDto> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.description,
  };

  private colCheck: BdDataColumn<LDAPSettingsDto> = {
    id: 'check',
    name: 'Check',
    data: (r) => `Check connection to ${r.server}`,
    component: LdapCheckActionComponent,
    width: '40px',
    actionDisabled: (r) => this.isEditMode(r),
  };

  private colEdit: BdDataColumn<LDAPSettingsDto> = {
    id: 'edit',
    name: 'Edit',
    data: (r) => `Edit server ${r.server}`,
    component: LdapEditActionComponent,
    icon: (r) => 'edit',
    width: '40px',
  };

  private colDelete: BdDataColumn<LDAPSettingsDto> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove server ${r.server}`,
    action: (r) => this.settings.removeLdapServer(r),
    icon: (r) => 'delete',
    width: '40px',
    actionDisabled: (r) => this.isEditMode(r),
  };

  /* template */ columns: BdDataColumn<LDAPSettingsDto>[] = [this.colServer, this.colDescription, this.colCheck, this.colEdit, this.colDelete];
  /* template */ tempServer: Partial<LDAPSettingsDto>;
  /* template */ checkResult$ = new Subject<string>();
  private selectedServerId: string;

  private subscription: Subscription;

  constructor(public settings: SettingsService, private router: Router, private areas: NavAreasService) {}

  ngOnInit(): void {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      if (!route?.params || !route.params['id']) {
        this.selectedServerId = null;
        return;
      }
      this.selectedServerId = route.params['id'];
    });
  }

  /* template */ onReorder(order: DragReorderEvent<LDAPSettingsDto>) {
    if (order.previousIndex === order.currentIndex) {
      return;
    }

    moveItemInArray(this.settings.settings$.value.auth.ldapSettings, order.previousIndex, order.currentIndex);
    this.settings.serversReordered();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isEditMode(r) {
    return this.selectedServerId === r.id;
  }
}
