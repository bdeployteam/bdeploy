import { moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { BdDataTableComponent, DragReorderEvent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-ldap-tab',
  templateUrl: './ldap-tab.component.html',
  styleUrls: ['./ldap-tab.component.css'],
})
export class LdapTabComponent implements OnInit {
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
    action: (r) => this.checkServer(r),
    icon: (r) => 'bolt',
    width: '40px',
  };

  private colEdit: BdDataColumn<LDAPSettingsDto> = {
    id: 'edit',
    name: 'Edit',
    data: (r) => `Edit server ${r.server}`,
    action: (r) => this.editServer(r),
    icon: (r) => 'edit',
    width: '40px',
  };

  private colDelete: BdDataColumn<LDAPSettingsDto> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove server ${r.server}`,
    action: (r) => this.removeServer(r),
    icon: (r) => 'delete',
    width: '40px',
  };

  /* template */ columns: BdDataColumn<LDAPSettingsDto>[] = [this.colServer, this.colDescription, this.colCheck, this.colEdit, this.colDelete];
  /* template */ tempServer: Partial<LDAPSettingsDto>;
  /* template */ checkResult$ = new Subject<string>();

  @ViewChild(BdDataTableComponent) table: BdDataTableComponent<LDAPSettingsDto>;
  constructor(public settings: SettingsService, private router: Router) {}

  ngOnInit(): void {}

  private editServer(server: LDAPSettingsDto) {
    this.router.navigate(['', { outlets: { panel: ['panels', 'admin', 'edit-ldap-server'] } }]);
    this.settings.setSelectedServer(server);
  }

  private removeServer(server: LDAPSettingsDto): void {
    this.settings.settings$.value.auth.ldapSettings.splice(this.settings.settings$.value.auth.ldapSettings.indexOf(server), 1);
    this.table.update();
  }

  private checkServer(server: LDAPSettingsDto): void {
    this.router.navigate(['', { outlets: { panel: ['panels', 'admin', 'check-ldap-server'] } }]);
    this.settings.setSelectedServer(server);
  }

  /* template */ onReorder(order: DragReorderEvent<LDAPSettingsDto>) {
    if (order.previousIndex === order.currentIndex) {
      return;
    }

    moveItemInArray(this.settings.settings$.value.auth.ldapSettings, order.previousIndex, order.currentIndex);
    this.table.update();
  }
}
