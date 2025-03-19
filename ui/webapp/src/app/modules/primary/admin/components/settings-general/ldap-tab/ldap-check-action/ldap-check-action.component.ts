import { Component, Input } from '@angular/core';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { BdPanelButtonComponent } from '../../../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  CellComponent
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-ldap-check-action',
    templateUrl: './ldap-check-action.component.html',
    imports: [BdPanelButtonComponent]
})
export class LdapCheckActionComponent implements CellComponent<LDAPSettingsDto, string> {
  @Input() record: LDAPSettingsDto;
  @Input() column: BdDataColumn<LDAPSettingsDto, string>;
}
