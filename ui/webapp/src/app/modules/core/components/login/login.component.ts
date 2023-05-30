import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@auth0/auth0-angular';
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
    private auth0: AuthService
  ) {}

  ngOnInit(): void {
    this.tokenSubscription = this.auth.getTokenSubject().subscribe((token) => {
      if (token !== null) {
        const ret = this.route.snapshot.queryParams['returnUrl'];
        const returnUrl = ret ? ret : '/';

        this.router.navigateByUrl(returnUrl);
      }
    });
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
    this.auth0.loginWithPopup().subscribe(() => {
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
                this.loginFailedMessage = `User "${this.user}" failed to authenticate`;
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

  /* template */ onLogoClick() {
    this.logoClass = 'local-hinge';
    setTimeout(() => (this.logoClass = null), 2000);
  }
}
