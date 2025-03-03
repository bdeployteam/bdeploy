import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Observable, of, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
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
import { MailReceiverSettingsDto } from '../../../../../models/gen.dtos';
import { cloneDeep } from 'lodash-es';
import { DirtyableDialog } from '../../../../core/guards/dirty-dialog.guard';

@Component({
    selector: 'app-mail-receiving',
    templateUrl: './mail-receiving.component.html',
  imports: [BdFormToggleComponent, FormsModule, BdFormInputComponent, TrimmedValidator, ServerConnectionUrlSyntaxValidator, BdButtonComponent, AsyncPipe, BdDialogComponent, BdDialogContentComponent, BdDialogToolbarComponent]
})
export class MailReceivingComponent  implements OnInit, DirtyableDialog, OnDestroy {
  private readonly settings = inject(SettingsService);
  private readonly areas = inject(NavAreasService);
  protected saving$ = new BehaviorSubject<boolean>(false);

  protected mailReceiverSettings: Partial<MailReceiverSettingsDto>;
  protected connectionTestStatusMsg$ = new BehaviorSubject<string>(null);

  private subscription: Subscription;

  @ViewChild('imapConnectionData') public form: NgForm;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  public ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.mailReceiverSettings = cloneDeep(this.settings.settings$.value.mailReceiverSettings);
  }

  public ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return !!this.form && this.form.dirty;
  }

  public canSave() {
    return !!this.form && this.form.valid;
  }

  public doSave(): Observable<any> {
    this.saving$.next(true);
    this.settings.updateMailReceiverSettings(this.mailReceiverSettings as MailReceiverSettingsDto);
    return of(null);
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.saving$.next(false);
      this.form.resetForm(this.form.value);
      this.areas.closePanel();
    });
  }

  protected onPasswordChange(newValue: string) {
    this.settings.settings$.value.mailReceiverSettings.password = newValue || null;
  }

  protected clearMessages() {
    this.connectionTestStatusMsg$.next('');
  }

  protected testConnection() {
    this.connectionTestStatusMsg$.next('');
    this.settings
      .testReceiverConnection(this.settings.settings$.value.mailReceiverSettings)
      .pipe(
        tap((response: boolean) => {
          if (response) {
            this.connectionTestStatusMsg$.next('Success!');
          }
        }),
      )
      .subscribe();
  }

}
