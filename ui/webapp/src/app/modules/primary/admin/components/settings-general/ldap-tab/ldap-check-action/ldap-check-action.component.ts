import { Component, Input } from '@angular/core';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-ldap-check-action',
  templateUrl: './ldap-check-action.component.html',
})
export class LdapCheckActionComponent {
  @Input() record: LDAPSettingsDto;
}
