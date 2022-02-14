import { Component, Input } from '@angular/core';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'ldap-check-action',
  templateUrl: './ldap-check-action.component.html',
})
export class LdapCheckActionComponent {
  @Input() record: LDAPSettingsDto;
}
