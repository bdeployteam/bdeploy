import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { BehaviorSubject, finalize, Observable, Subscription } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { randomString } from 'src/app/modules/core/utils/object.utils';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../core/validators/trimmed.directive';
import { BdFormToggleComponent } from '../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';

@Component({
    selector: 'app-add-ldap-server',
    templateUrl: './add-ldap-server.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, TrimmedValidator, BdFormToggleComponent, BdButtonComponent]
})
export class AddLdapServerComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly settings = inject(SettingsService);
  private readonly areas = inject(NavAreasService);

  protected tempServer: Partial<LDAPSettingsDto>;
  protected saving$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.tempServer = {
      server: 'ldaps://',
      accountPattern: '(objectCategory=person)',
      accountUserName: 'sAMAccountName',
      accountFullName: 'displayName',
      accountEmail: 'mail',
      groupPattern: '(objectClass=group)',
      groupName: 'cn',
      groupDescription: 'description',
      syncEnabled: true,
      id: randomString(10),
    };
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(
        finalize(() => {
          this.saving$.next(false);
        }),
      )
      .subscribe(() => {
        this.areas.closePanel();
        this.subscription?.unsubscribe();
      });
  }

  public doSave(): Observable<boolean> {
    this.saving$.next(true);
    return this.settings.addLdapServer(this.tempServer);
  }
}
