import { Component, Input } from '@angular/core';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'ldap-edit-action',
  templateUrl: './ldap-edit-action.component.html',
})
export class LdapEditActionComponent {
  @Input() record: LDAPSettingsDto;
}
