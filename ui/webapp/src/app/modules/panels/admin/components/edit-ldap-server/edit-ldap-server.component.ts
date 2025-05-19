import { AfterViewInit, ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, debounceTime, Observable, of, Subscription } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../core/validators/trimmed.directive';
import { BdFormToggleComponent } from '../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';
import { PatternValidator } from '../../../../core/validators/pattern-validator.directive';

@Component({
    selector: 'app-edit-ldap-server',
    templateUrl: './edit-ldap-server.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, TrimmedValidator, BdFormToggleComponent, BdButtonComponent, AsyncPipe, PatternValidator]
})
export class EditLdapServerComponent implements OnInit, OnDestroy, AfterViewInit, DirtyableDialog {
  private readonly settings = inject(SettingsService);
  private readonly areas = inject(NavAreasService);

  protected tempServer: Partial<LDAPSettingsDto>;
  protected initialServer: Partial<LDAPSettingsDto>;
  protected isDirty$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;

  @ViewChild('form') public form: NgForm;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.settings.settings$]).subscribe(([route, settings]) => {
        this.initialServer = settings.auth.ldapSettings.find((a) => a.id === route.params['id']);
        if (!settings || !route?.params?.['id'] || !this.initialServer) {
          this.areas.closePanel();
          return;
        }
        this.tempServer = cloneDeep(this.initialServer);
      })
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.isDirty$.next(this.isDirty());
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    if (this.tempServer && this.initialServer) {
      return isDirty(this.tempServer, this.initialServer);
    }
    return false;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  public doSave(): Observable<void> {
    return of(this.settings.editLdapServer(this.tempServer, this.initialServer));
  }

  private reset() {
    this.tempServer = this.initialServer;
    this.areas.closePanel();
  }
}
