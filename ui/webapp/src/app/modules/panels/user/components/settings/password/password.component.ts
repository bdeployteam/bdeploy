import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';

@Component({
  selector: 'app-password',
  templateUrl: './password.component.html',
  styleUrls: ['./password.component.css'],
})
export class PasswordComponent implements OnInit {
  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ passOrig = '';
  /* template */ passNew = '';
  /* template */ passVerify = '';
  /* template */ remoteError: string = null;

  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  constructor(private auth: AuthenticationService) {}

  ngOnInit(): void {}

  onSave(): void {
    this.loading$.next(true);
    this.auth
      .changePassword({ user: this.auth.getUsername(), currentPassword: this.passOrig, newPassword: this.passNew })
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe(
        (_) => {
          this.tb.closePanel();
        },
        (err) => {
          if (err instanceof HttpErrorResponse) {
            this.remoteError = err.statusText;
          }
        }
      );
  }
}
