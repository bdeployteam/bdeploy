import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@auth0/auth0-angular';
import OktaAuth, { OktaAuthOptions } from '@okta/okta-auth-js';
import { BehaviorSubject, Subscription, firstValueFrom, forkJoin } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SpecialAuthenticators } from 'src/app/models/gen.dtos';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';
import { BdDialogComponent } from '../bd-dialog/bd-dialog.component';
import { BdDialogContentComponent } from '../bd-dialog-content/bd-dialog-content.component';
import { BdLogoComponent } from '../bd-logo/bd-logo.component';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BdFormInputComponent } from '../bd-form-input/bd-form-input.component';
import { MatError } from '@angular/material/form-field';
import { MatButton } from '@angular/material/button';
import { BdButtonComponent } from '../bd-button/bd-button.component';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.css'],
    imports: [BdDialogComponent, BdDialogContentComponent, BdLogoComponent, NgClass, FormsModule, BdFormInputComponent, MatError, MatButton, BdButtonComponent]
})
export class LoginComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth0 = inject(AuthService);
  private readonly snackbar = inject(MatSnackBar);
  protected readonly auth = inject(AuthenticationService);
  protected readonly cfg = inject(ConfigService);

  private tokenSubscription: Subscription;

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected user: string;
  protected pass: string;

  protected logoClass: string;

  public loginFailed = false;
  public loginFailedMessage: string;

  ngOnInit(): void {
    this.tokenSubscription = this.auth.getTokenSubject().subscribe((token) => {
      if (token !== null) {
        this.loading$.next(true);
        const ret = this.route.snapshot.queryParams['returnUrl'];
        const returnUrl = ret || '/';

        this.router.navigateByUrl(returnUrl);
        this.snackbar.dismiss();
        this.loading$.next(false);
      }
    });

    // there might be snackbars open if being logged out due to insufficient permissions.
    this.snackbar.dismiss();
  }

  ngOnDestroy(): void {
    this.tokenSubscription?.unsubscribe();
  }

  protected onSubmit(): void {
    this.loading$.next(true);
    this.auth.authenticate(this.user, this.pass).subscribe({
      next: () => {
        // loading$ stays on, wait for redirect
        console.log(`User "${this.user}" successfully logged in`);
      },
      error: (error) => {
        this.loading$.next(false);
        if (error.status === 401) {
          this.loginFailedMessage = `User "${this.user}" failed to authenticate`;
        } else {
          this.loginFailedMessage = `Error authenticating "${this.user}"`;
        }

        console.error(this.loginFailedMessage, error);
        this.loginFailed = true;
      },
    });
  }

  protected loginAuth0() {
    this.loading$.next(true);
    this.auth0
      .loginWithPopup()
      .pipe(
        catchError((e) => {
          this.loading$.next(false);
          throw e;
        }),
      )
      .subscribe(() => {
        forkJoin([firstValueFrom(this.auth0.user$), this.auth0.getAccessTokenSilently()]).subscribe(([u, t]) => {
          this.user = u.email;
          this.auth.authenticate(u.email, t, SpecialAuthenticators.AUTH0).subscribe({
            next: () => {
              console.log(`User "${u.email}" successfully logged in`);
            },
            error: (error) => {
              this.loading$.next(false);
              if (error.status === 401) {
                this.loginFailedMessage = `User "${u.email}" failed to authenticate`;
              } else {
                this.loginFailedMessage = `Error authenticating "${u.email}"`;
              }

              console.error(this.loginFailedMessage, error);
              this.loginFailed = true;
            },
          });
        });
      });
  }

  protected loginOkta() {
    const config: OktaAuthOptions = {
      issuer: `https://${this.cfg.webAuthCfg.okta.domain}/`,
      clientId: this.cfg.webAuthCfg.okta.clientId,
    };

    const client = new OktaAuth(config);
    this.loading$.next(true);
    client.token
      .getWithPopup({ popupTitle: 'Login using Okta' })
      .then(async (t) => {
        const u = await client.token.getUserInfo(t.tokens.accessToken, t.tokens.idToken);

        this.user = u.email;
        this.auth.authenticate(u.email, JSON.stringify(t.tokens.accessToken), SpecialAuthenticators.OKTA).subscribe({
          next: () => {
            console.log(`User "${u.email}" successfully logged in`);
          },
          error: (error) => {
            this.loading$.next(false);
            if (error.status === 401) {
              this.loginFailedMessage = `User "${u.email}" failed to authenticate`;
            } else {
              this.loginFailedMessage = `Error authenticating "${u.email}"`;
            }

            console.error(this.loginFailedMessage, error);
            this.loginFailed = true;
          },
        });
      })
      .catch((err) => {
        this.loading$.next(false);
        console.log('Okta error', err);
        this.loginFailedMessage = `Error authenticating with Okta`;
        this.loginFailed = true;
      });
  }

  protected onLogoClick() {
    this.logoClass = 'local-hinge';
    setTimeout((): void  => (this.logoClass = null), 2000);
  }
}
