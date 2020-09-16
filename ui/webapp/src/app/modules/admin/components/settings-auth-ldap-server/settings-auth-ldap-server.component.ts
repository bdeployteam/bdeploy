import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { AuthAdminService } from '../../services/auth-admin.service';

@Component({
  selector: 'app-settings-auth-ldap-server',
  templateUrl: './settings-auth-ldap-server.component.html',
  styleUrls: ['./settings-auth-ldap-server.component.css']
})
export class SettingsAuthLdapServerComponent implements OnInit {

  loading = false;

  constructor(@Inject(MAT_DIALOG_DATA) public server: LDAPSettingsDto, private authAdminService: AuthAdminService) {}

  ngOnInit() {
    if (!this.server) {
      this.loading = true;
      this.server = <LDAPSettingsDto>{};
      this.authAdminService.createUuid().pipe(finalize(() => (this.loading = false))).subscribe(uuid => this.server.id = uuid);
      this.server.server = 'ldaps://';
      this.server.accountPattern = '(objectCategory=person)';
      this.server.accountUserName = 'sAMAccountName';
      this.server.accountFullName = 'displayName';
      this.server.accountEmail = 'mail';
    }
  }

}
