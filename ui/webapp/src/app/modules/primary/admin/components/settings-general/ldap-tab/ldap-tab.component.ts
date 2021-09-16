import { moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, forwardRef, Inject, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Subject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { BdDataTableComponent, DragReorderEvent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { ACTION_APPLY, ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { AuthAdminService } from '../../../services/auth-admin.service';
import { SettingsGeneralComponent } from '../settings-general.component';

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

  @ViewChild('addEditServer') addEditServerTemplate: TemplateRef<any>;
  @ViewChild('checkServer') checkServerTemplate: TemplateRef<any>;
  @ViewChild('addEditForm', { static: false }) addEditForm: NgForm;
  @ViewChild(BdDataTableComponent) table: BdDataTableComponent<LDAPSettingsDto>;

  constructor(
    public settings: SettingsService,
    @Inject(forwardRef(() => SettingsGeneralComponent)) private parent: SettingsGeneralComponent,
    private auth: AuthAdminService
  ) {}

  ngOnInit(): void {}

  public addServer(): void {
    this.tempServer = {
      server: 'ldaps://',
      accountPattern: '(objectCategory=person)',
      accountUserName: 'sAMAccountName',
      accountFullName: 'displayName',
      accountEmail: 'mail',
    };
    this.parent.dialog
      .message({
        header: 'Add Server',
        icon: 'add',
        template: this.addEditServerTemplate,
        actions: [ACTION_CANCEL, ACTION_OK],
        validation: () => !this.addEditForm || this.addEditForm.valid,
      })
      .subscribe((r) => {
        if (r) {
          this.settings.settings$.value.auth.ldapSettings.push(this.tempServer as LDAPSettingsDto);
          this.table.update();
        }
      });
  }

  private editServer(server: LDAPSettingsDto) {
    this.tempServer = { ...server };
    this.parent.dialog
      .message({
        header: 'Edit Server',
        icon: 'edit',
        template: this.addEditServerTemplate,
        actions: [ACTION_CANCEL, ACTION_APPLY],
        validation: () => !this.addEditForm || this.addEditForm.valid,
      })
      .subscribe((r) => {
        if (r) {
          this.settings.settings$.value.auth.ldapSettings.splice(
            this.settings.settings$.value.auth.ldapSettings.indexOf(server),
            1,
            this.tempServer as LDAPSettingsDto
          );
          this.table.update();
        }
      });
  }

  private removeServer(server: LDAPSettingsDto): void {
    this.settings.settings$.value.auth.ldapSettings.splice(this.settings.settings$.value.auth.ldapSettings.indexOf(server), 1);
    this.table.update();
  }

  private checkServer(server: LDAPSettingsDto): void {
    this.parent.dialog
      .message({ header: 'Checking Server', icon: 'bolt', template: this.checkServerTemplate, actions: [{ name: 'CLOSE', result: null, confirm: true }] })
      .subscribe();
    this.checkResult$.next(`Checking ...`);
    this.auth.testLdapServer(server).subscribe((r) => {
      this.checkResult$.next(r);
    });
  }

  /* template */ onReorder(order: DragReorderEvent<LDAPSettingsDto>) {
    if (order.previousIndex === order.currentIndex) {
      return;
    }

    moveItemInArray(this.settings.settings$.value.auth.ldapSettings, order.previousIndex, order.currentIndex);
    this.table.update();
  }
}
