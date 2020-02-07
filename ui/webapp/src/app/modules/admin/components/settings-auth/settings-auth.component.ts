import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { cloneDeep } from 'lodash';
import { Observable, of } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { CanComponentDeactivate } from 'src/app/modules/shared/guards/can-deactivate.guard';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { SettingsService } from '../../../core/services/settings.service';
import { SettingsAuthLdapServerComponent } from '../settings-auth-ldap-server/settings-auth-ldap-server.component';

@Component({
  selector: 'app-settings-auth',
  templateUrl: './settings-auth.component.html',
  styleUrls: ['./settings-auth.component.css'],
  providers: [SettingsService]
})
export class SettingsAuthComponent implements OnInit, CanComponentDeactivate {
  constructor(public settings: SettingsService, private dialog: MatDialog, private messageBoxService: MessageboxService) { }

  ngOnInit() {
  }

  up(i: number) {
    this.getLdapSettings().splice(i - 1, 0, this.getLdapSettings().splice(i, 1)[0]);
  }

  down(i: number) {
    this.getLdapSettings().splice(i + 1, 0, this.getLdapSettings().splice(i, 1)[0]);
  }

  private getLdapSettings() {
    return this.settings.getSettings().auth.ldapSettings;
  }

  add() {
    this.dialog.open(SettingsAuthLdapServerComponent, {
      width: '500px',
      data: null,
    }).afterClosed().subscribe(r => {
      if (r) {
        this.getLdapSettings().push(r);
      }
    });
  }

  remove(i: number) {
    this.getLdapSettings().splice(i, 1);
  }

  edit(s: LDAPSettingsDto, i: number) {
    this.dialog.open(SettingsAuthLdapServerComponent, {
      width: '500px',
      data: cloneDeep(s),
    }).afterClosed().subscribe(r => {
      if (r) {
        this.getLdapSettings().splice(i, 1, r);
      }
    });
  }

  canDeactivate(): Observable<boolean> {
    if (!this.settings.isDirty()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Settings were modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

}
