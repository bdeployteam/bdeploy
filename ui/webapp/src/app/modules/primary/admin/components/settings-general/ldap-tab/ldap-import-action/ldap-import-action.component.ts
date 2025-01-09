import { Component, Input, OnInit, inject } from '@angular/core';
import { BehaviorSubject, of } from 'rxjs';
import { Actions, LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { BdPanelButtonComponent } from '../../../../../../core/components/bd-panel-button/bd-panel-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-ldap-import-action',
    templateUrl: './ldap-import-action.component.html',
    imports: [BdPanelButtonComponent, AsyncPipe]
})
export class LdapImportActionComponent implements OnInit {
  @Input() record: LDAPSettingsDto;

  private readonly actions = inject(ActionsService);
  private readonly id$ = new BehaviorSubject<string>(null);

  protected mappedImport$ = this.actions.action([Actions.LDAP_SYNC], of(false), null, null, this.id$);

  ngOnInit(): void {
    this.id$.next(this.record.id);
  }
}
