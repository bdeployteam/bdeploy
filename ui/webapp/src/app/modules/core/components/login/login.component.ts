import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
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

  private returnUrl: string;

  public loading = false;

  public username = new FormControl('', [Validators.required]);
  public password = new FormControl('', [Validators.required]);

  public loginFailed = false;
  public loginFailedMessage;

  constructor(
    private loggingService: LoggingService,
    private route: ActivatedRoute,
    private router: Router,
    public auth: AuthenticationService
  ) {}

  getErrorMessage(ctrl: FormControl): string {
    return ctrl.hasError('required') ? 'You must enter a value' : 'Unknown error';
  }

  ngOnInit(): void {
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';

    this.tokenSubscription = this.auth.getTokenSubject().subscribe((token) => {
      if (token !== null) {
        this.router.navigate([this.returnUrl]);
        this.log.debug('auth.token = ' + this.auth.getToken());
      }
    });
  }

  ngOnDestroy(): void {
    this.tokenSubscription.unsubscribe();
  }

  onSubmit(): void {
    if (!this.username.valid || !this.password.valid) {
      this.log.warn('invalid username or password input!');
      return;
    }

    this.log.info('Login attempt for user "' + this.username.value + '"');

    this.loading = true;
    this.auth.authenticate(this.username.value, this.password.value).subscribe(
      (result) => {
        this.log.info('User "' + this.username.value + '" successfully logged in');
        this.loggingService.dismissOpenMessage(); // close any open "error" popup.
      },
      (error) => {
        if (error.status === 401) {
          this.loginFailedMessage = 'User "' + this.username.value + '" failed to authenticate';
        } else {
          this.loginFailedMessage = new ErrorMessage('Error authenticating "' + this.username.value + '"', error);
        }

        this.log.error(this.loginFailedMessage);
        this.loginFailed = true;
        this.loading = false;
      }
    );
  }
}
