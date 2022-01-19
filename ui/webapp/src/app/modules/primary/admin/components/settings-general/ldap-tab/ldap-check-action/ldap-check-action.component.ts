import { Component, Input, OnInit } from '@angular/core';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'ldap-check-action',
  templateUrl: './ldap-check-action.component.html',
  styleUrls: ['./ldap-check-action.component.css'],
})
export class LdapCheckActionComponent implements OnInit {
  @Input() record: LDAPSettingsDto;

  constructor() {}

  ngOnInit(): void {}
}
