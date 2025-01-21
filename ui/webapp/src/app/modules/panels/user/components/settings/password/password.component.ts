import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { BdDialogComponent } from '../../../../../core/components/bd-dialog/bd-dialog.component';

import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { FormsModule } from '@angular/forms';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { PasswordVerificationValidator } from '../../../../../core/validators/password-verification.directive';
import { PasswordStrengthMeterComponent } from 'angular-password-strength-meter';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';

@Component({
    selector: 'app-password',
    templateUrl: './password.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, PasswordVerificationValidator, PasswordStrengthMeterComponent, BdButtonComponent]
})
export class PasswordComponent {
  private readonly auth = inject(AuthenticationService);

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected passOrig = '';
  protected passNew = '';
  protected passVerify = '';
  protected remoteError: string = null;

  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  protected onSave(): void {
    this.loading$.next(true);
    this.auth
      .changePassword({
        user: this.auth.getCurrentUsername(),
        currentPassword: this.passOrig,
        newPassword: this.passNew,
      })
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe({
        next: () => {
          this.tb.closePanel();
        },
        error: (err) => {
          if (err instanceof HttpErrorResponse) {
            this.remoteError = err.statusText;
          }
        },
      });
  }
}
