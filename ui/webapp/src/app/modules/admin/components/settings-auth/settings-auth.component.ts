import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { cloneDeep } from 'lodash-es';
import { Observable, of } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { TextboxComponent } from 'src/app/modules/shared/components/textbox/textbox.component';
import { CanComponentDeactivate } from 'src/app/modules/shared/guards/can-deactivate.guard';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { SettingsService } from '../../../core/services/settings.service';
import { AuthAdminService } from '../../services/auth-admin.service';
import { SettingsAuthLdapServerComponent } from '../settings-auth-ldap-server/settings-auth-ldap-server.component';
import { SettingsAuthTestUserComponent } from '../settings-auth-test-user/settings-auth-test-user.component';

@Component({
  selector: 'app-settings-auth',
  templateUrl: './settings-auth.component.html',
  styleUrls: ['./settings-auth.component.css'],
  providers: [SettingsService],
})
export class SettingsAuthComponent implements OnInit, CanComponentDeactivate {
  testRunning = false;

  constructor(
    public settings: SettingsService,
    private authAdminService: AuthAdminService,
    private dialog: MatDialog,
    private messageBoxService: MessageboxService
  ) {}

  ngOnInit() {}

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
    this.dialog
      .open(SettingsAuthLdapServerComponent, {
        width: '500px',
        disableClose: true,
        data: null,
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          this.getLdapSettings().push(r);
        }
      });
  }

  remove(i: number) {
    this.getLdapSettings().splice(i, 1);
  }

  edit(s: LDAPSettingsDto, i: number) {
    this.dialog
      .open(SettingsAuthLdapServerComponent, {
        width: '500px',
        disableClose: true,
        data: cloneDeep(s),
      })
      .afterClosed()
      .subscribe((r) => {
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

  testUser() {
    this.dialog.open(SettingsAuthTestUserComponent, {
      width: '800px',
    });
  }

  testServer(i: number) {
    this.testRunning = true;
    const dto: LDAPSettingsDto = this.getLdapSettings()[i];
    this.authAdminService.testLdapServer(dto).subscribe((r) => {
      if (r === 'OK') {
        this.messageBoxService
          .open({
            title: 'Connection Test',
            message: 'Server ' + dto.server + ' responded as expected.',
            mode: MessageBoxMode.INFO,
          })
          .subscribe((_) => {
            this.testRunning = false;
          });
      } else {
        this.dialog
          .open(TextboxComponent, {
            width: '80%',
            height: '600px',
            data: { title: 'Connection Test Failure', text: r },
            closeOnNavigation: true,
          })
          .afterClosed()
          .subscribe((e) => {
            this.testRunning = false;
          });
      }
    });
  }
}
