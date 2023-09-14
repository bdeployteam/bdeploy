import { HttpErrorResponse } from '@angular/common/http';
import { Component, ViewChild, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';

@Component({
  selector: 'app-password',
  templateUrl: './password.component.html',
})
export class PasswordComponent {
  private auth = inject(AuthenticationService);

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected passOrig = '';
  protected passNew = '';
  protected passVerify = '';
  protected remoteError: string = null;

  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

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
