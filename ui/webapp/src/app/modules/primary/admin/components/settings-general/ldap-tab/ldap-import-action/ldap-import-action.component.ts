import { Component, Input } from '@angular/core';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'ldap-import-action',
  templateUrl: './ldap-import-action.component.html',
})
export class LdapImportActionComponent {
  @Input() record: LDAPSettingsDto;
}
