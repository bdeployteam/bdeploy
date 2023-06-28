import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@auth0/auth0-angular';
import OktaAuth, { OktaAuthOptions } from '@okta/okta-auth-js';
import { BehaviorSubject, Subscription, firstValueFrom, forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { SpecialAuthenticators } from 'src/app/models/gen.dtos';
import { AuthenticationService } from '../../services/authentication.service';
import { ConfigService } from '../../services/config.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent implements OnInit, OnDestroy {
  private tokenSubscription: Subscription;

  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ user: string;
  /* template */ pass: string;

  /* template */ logoClass: string;

  public loginFailed = false;
  public loginFailedMessage;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    public auth: AuthenticationService,
    public cfg: ConfigService,
    private auth0: AuthService,
    private snackbar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.tokenSubscription = this.auth.getTokenSubject().subscribe((token) => {
      if (token !== null) {
        this.loading$.next(true);
        const ret = this.route.snapshot.queryParams['returnUrl'];
        const returnUrl = ret ? ret : '/';

        this.router.navigateByUrl(returnUrl);
        this.snackbar.dismiss();
        this.loading$.next(false);
      }
    });

    // there might be snackbars open if being logged out due to insufficient permissions.
    this.snackbar.dismiss();
  }

  ngOnDestroy(): void {
    this.tokenSubscription.unsubscribe();
  }

  /* template */ onSubmit(): void {
    this.loading$.next(true);
    this.auth
      .authenticate(this.user, this.pass)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe({
        next: () => {
          console.log(`User "${this.user}" successfully logged in`);
        },
        error: (error) => {
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

  /* template */ loginAuth0() {
    this.loading$.next(true);
    this.auth0
      .loginWithPopup()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe(() => {
        forkJoin([
          firstValueFrom(this.auth0.user$),
          this.auth0.getAccessTokenSilently(),
        ]).subscribe(([u, t]) => {
          this.user = u.email;
          this.loading$.next(true);
          this.auth
            .authenticate(u.email, t, SpecialAuthenticators.AUTH0)
            .pipe(finalize(() => this.loading$.next(false)))
            .subscribe({
              next: () => {
                console.log(`User "${u.email}" successfully logged in`);
              },
              error: (error) => {
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

  /* template */ loginOkta() {
    const config: OktaAuthOptions = {
      issuer: `https://${this.cfg.webAuthCfg.okta.domain}/`,
      clientId: this.cfg.webAuthCfg.okta.clientId,
    };

    const client = new OktaAuth(config);
    this.loading$.next(true);
    client.token
      .getWithPopup({ popupTitle: 'Login using Okta' })
      .then(async (t) => {
        console.log(t);
        const u = await client.token.getUserInfo(
          t.tokens.accessToken,
          t.tokens.idToken
        );

        this.user = u.email;
        this.loading$.next(true);
        this.auth
          .authenticate(
            u.email,
            JSON.stringify(t.tokens.accessToken),
            SpecialAuthenticators.OKTA
          )
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe({
            next: () => {
              console.log(`User "${u.email}" successfully logged in`);
            },
            error: (error) => {
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
        console.log(err);
        this.loginFailedMessage = `Error authenticating with Okta`;
        this.loginFailed = true;
      })
      .finally(() => this.loading$.next(false));
  }

  /* template */ onLogoClick() {
    this.logoClass = 'local-hinge';
    setTimeout(() => (this.logoClass = null), 2000);
  }
}
