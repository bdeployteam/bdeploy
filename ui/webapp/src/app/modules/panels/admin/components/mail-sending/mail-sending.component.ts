import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, map, Observable, of, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { cloneDeep } from 'lodash-es';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { BdFormToggleComponent } from '../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { FormsModule, NgForm } from '@angular/forms';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../core/validators/trimmed.directive';
import { ServerConnectionUrlSyntaxValidator } from '../../../../core/validators/server-connection-url-syntax-validator.directive';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { NavAreasService } from '../../../../core/services/nav-areas.service';
import { MailSenderSettingsDto } from '../../../../../models/gen.dtos';
import { DirtyableDialog } from '../../../../core/guards/dirty-dialog.guard';

@Component({
    selector: 'app-mail-sending',
    templateUrl: './mail-sending.component.html',
  imports: [BdFormToggleComponent, FormsModule, BdFormInputComponent, TrimmedValidator, ServerConnectionUrlSyntaxValidator, BdButtonComponent, AsyncPipe, BdDialogComponent, BdDialogContentComponent, BdDialogToolbarComponent]
})
export class MailSendingComponent implements OnInit, DirtyableDialog, OnDestroy {
  private readonly settings = inject(SettingsService);
  private readonly areas = inject(NavAreasService);

  protected mailSenderSettings: Partial<MailSenderSettingsDto>;
  protected connectionTestStatusMsg$ = new BehaviorSubject<string>(null);
  protected mailSendingTestStatusMsg$ = new BehaviorSubject<string>(null);
  protected saving$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('receiver') receiverForm: NgForm;
  @ViewChild('smtpConnectionData') smtpConnectionDataForm: NgForm;

  public ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.mailSenderSettings = cloneDeep(this.settings.settings$.value.mailSenderSettings);
  }

  public ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return !this.settings.isMailSenderSettingsEqualToOriginal(this.mailSenderSettings as MailSenderSettingsDto);
  }

  public canSave() {
    return !this.mailSenderSettings.enabled ||
      (this.receiverForm.valid && this.smtpConnectionDataForm.valid);
  }

  public doSave(): Observable<any> {
    this.saving$.next(true);
    this.settings.updateMailSenderSettings(this.mailSenderSettings as MailSenderSettingsDto);
    return of(null);
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.saving$.next(false);
      this.areas.closePanel();
    });
  }

  protected onPasswordChange(e) {
    this.settings.settings$.value.mailSenderSettings.password = e || null;
  }

  protected clearMessages() {
    this.connectionTestStatusMsg$.next('');
    this.mailSendingTestStatusMsg$.next('');
  }

  protected testConnection() {
    this.connectionTestStatusMsg$.next('');
    this.settings
      .testSenderConnection(this.settings.settings$.value.mailSenderSettings)
      .pipe(
        tap((response: boolean) => {
          if (response) {
            this.connectionTestStatusMsg$.next('Success!');
          }
        }),
      )
      .subscribe();
  }

  protected sendTestMail() {
    this.mailSendingTestStatusMsg$.next('');
    this.settings
      .sendTestMail(this.settings.settings$.value.mailSenderSettings)
      .pipe(
        tap((response: boolean) => {
          if (response) {
            this.mailSendingTestStatusMsg$.next('Success!');
          }
        }),
      )
      .subscribe();
  }
}

