import { Component, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { BdFormToggleComponent } from '../../../../../core/components/bd-form-toggle/bd-form-toggle.component';
import { FormsModule } from '@angular/forms';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../../core/validators/trimmed.directive';
import { ServerConnectionUrlSyntaxValidator } from '../../../../../core/validators/server-connection-url-syntax-validator.directive';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-mail-sending-tab',
    templateUrl: './mail-sending-tab.component.html',
    imports: [BdFormToggleComponent, FormsModule, BdFormInputComponent, TrimmedValidator, ServerConnectionUrlSyntaxValidator, BdButtonComponent, AsyncPipe]
})
export class MailSendingTabComponent {
  protected readonly settings = inject(SettingsService);

  protected connectionTestStatusMsg$ = new BehaviorSubject<string>(null);
  protected mailSendingTestStatusMsg$ = new BehaviorSubject<string>(null);

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
