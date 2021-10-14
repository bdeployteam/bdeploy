import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuthenticationService } from '../../services/authentication.service';

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

  constructor(private route: ActivatedRoute, private router: Router, public auth: AuthenticationService, private snackBar: MatSnackBar) {}

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
      .subscribe(
        (_) => {
          console.log(`User "${this.user}" successfully logged in`);
          this.snackBar.dismiss(); // potentially open snackbar by error handler(s).
        },
        (error) => {
          if (error.status === 401) {
            this.loginFailedMessage = `User "${this.user}" failed to authenticate`;
          } else {
            this.loginFailedMessage = `Error authenticating "${this.user}"`;
          }

          console.error(this.loginFailedMessage, error);
          this.loginFailed = true;
        }
      );
  }

  /* template */ onLogoClick() {
    this.logoClass = 'local-hinge';
    setTimeout(() => (this.logoClass = null), 2000);
  }
}
