import { Component, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-mail-receiving-tab',
  templateUrl: './mail-receiving-tab.component.html',
})
export class MailReceivingTabComponent {
  protected settings = inject(SettingsService);
  protected connectionTestStatusMsg$ = new BehaviorSubject<string>(null);

  protected onPasswordChange(e) {
    this.settings.settings$.value.mailReceiverSettings.password = e || null;
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
