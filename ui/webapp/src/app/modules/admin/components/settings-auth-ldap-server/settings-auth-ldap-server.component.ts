import { Component, Inject, OnInit } from '@angular/core';
import { MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { TextboxComponent } from 'src/app/modules/shared/components/textbox/textbox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { AuthAdminService } from '../../services/auth-admin.service';

@Component({
  selector: 'app-settings-auth-ldap-server',
  templateUrl: './settings-auth-ldap-server.component.html',
  styleUrls: ['./settings-auth-ldap-server.component.css'],
})
export class SettingsAuthLdapServerComponent implements OnInit {
  loading = false;

  constructor(
    @Inject(MAT_DIALOG_DATA) public server: LDAPSettingsDto,
    private authAdminService: AuthAdminService,
    private dialog: MatDialog,
    private messageBoxService: MessageboxService
  ) {}

  ngOnInit() {
    if (!this.server) {
      this.loading = true;
      this.server = <LDAPSettingsDto>{};
      this.authAdminService
        .createUuid()
        .pipe(finalize(() => (this.loading = false)))
        .subscribe((uuid) => (this.server.id = uuid));
      this.server.server = 'ldaps://';
      this.server.accountPattern = '(objectCategory=person)';
      this.server.accountUserName = 'sAMAccountName';
      this.server.accountFullName = 'displayName';
      this.server.accountEmail = 'mail';
    }
  }

  testServer() {
    this.loading = true;
    this.authAdminService.testLdapServer(this.server).subscribe((r) => {
      if (r === 'OK') {
        this.messageBoxService
          .open({
            title: 'Connection Test',
            message: 'Server responded as expected.',
            mode: MessageBoxMode.INFO,
          })
          .subscribe((_) => {
            this.loading = false;
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
            this.loading = false;
          });
      }
    });
  }
}
