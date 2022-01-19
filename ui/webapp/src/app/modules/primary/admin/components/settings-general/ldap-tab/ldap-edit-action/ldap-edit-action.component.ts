import { Component, Input, OnInit } from '@angular/core';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'ldap-edit-action',
  templateUrl: './ldap-edit-action.component.html',
  styleUrls: ['./ldap-edit-action.component.css'],
})
export class LdapEditActionComponent implements OnInit {
  @Input() record: LDAPSettingsDto;

  constructor() {}

  ngOnInit(): void {}
}
