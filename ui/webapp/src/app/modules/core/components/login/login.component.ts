import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuthenticationService } from '../../services/authentication.service';
import { ErrorMessage, Logger, LoggingService } from '../../services/logging.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent implements OnInit, OnDestroy {
  private log: Logger = this.loggingService.getLogger('LoginComponent');

  private tokenSubscription: Subscription;

  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ user: string;
  /* template */ pass: string;

  /* template */ logoClass: string;

  public loginFailed = false;
  public loginFailedMessage;

  constructor(private loggingService: LoggingService, private route: ActivatedRoute, private router: Router, public auth: AuthenticationService) {}

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
    this.log.info(`Login attempt for user "${this.user}"`);

    this.loading$.next(true);
    this.auth
      .authenticate(this.user, this.pass)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe(
        (_) => {
          this.log.info(`User "${this.user}" successfully logged in`);
          this.loggingService.dismissOpenMessage(); // close any open "error" popup.
        },
        (error) => {
          if (error.status === 401) {
            this.loginFailedMessage = `User "${this.user}" failed to authenticate`;
          } else {
            this.loginFailedMessage = new ErrorMessage(`Error authenticating "${this.user}"`, error);
          }

          this.log.error(this.loginFailedMessage);
          this.loginFailed = true;
        }
      );
  }

  /* template */ onLogoClick() {
    this.logoClass = 'local-hinge';
    setTimeout(() => (this.logoClass = null), 2000);
  }
}
