import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-settings-auth-ldap-server',
  templateUrl: './settings-auth-ldap-server.component.html',
  styleUrls: ['./settings-auth-ldap-server.component.css']
})
export class SettingsAuthLdapServerComponent implements OnInit {

  constructor(@Inject(MAT_DIALOG_DATA) public server: LDAPSettingsDto) {
  }

  ngOnInit() {
    if (!this.server) {
      this.server = <LDAPSettingsDto>{};
      this.server.server = 'ldaps://';
      this.server.accountPattern = '(objectCategory=person)';
      this.server.accountUserName = 'sAMAccountName';
      this.server.accountFullName = 'displayName';
      this.server.accountEmail = 'mail';
    }
  }

}
