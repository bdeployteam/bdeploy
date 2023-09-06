import { Component, Input, OnInit, inject } from '@angular/core';
import { BehaviorSubject, of } from 'rxjs';
import { Actions, LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { ActionsService } from 'src/app/modules/core/services/actions.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'ldap-import-action',
  templateUrl: './ldap-import-action.component.html',
})
export class LdapImportActionComponent implements OnInit {
  @Input() record: LDAPSettingsDto;

  private actions = inject(ActionsService);
  private id$ = new BehaviorSubject<string>(null);

  protected mappedImport$ = this.actions.action([Actions.LDAP_SYNC], of(false), null, null, this.id$);

  ngOnInit(): void {
    this.id$.next(this.record.id);
  }
}
