import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { NO_ERROR_HANDLING_HDR } from 'src/app/models/consts';
import { AuthenticationService } from '../services/authentication.service';
import { Logger, LoggingService } from '../services/logging.service';

@Injectable()
export class UnauthorizedInterceptor implements HttpInterceptor {
  log: Logger = this.loggingService.getLogger('UnauthorizedInterceptor');

  constructor(private auth: AuthenticationService, private loggingService: LoggingService, private router: Router) {}

  intercept(req: HttpRequest<any>, next: HttpHandler) {
    return next.handle(req).pipe(
      catchError((err) => {
        if (err.status === 401 && !req.headers.has(NO_ERROR_HANDLING_HDR)) {
          // API request unauthorized, log out the application
          this.log.debug('unauthorized request: ' + err.url);
          this.logout();
          return of(null);
        }
        return throwError(err);
      })
    );
  }

  logout(): void {
    this.router.navigate(['/login']).then(
      (result) => {
        this.auth.logout();
      },
      (r) => {
        this.log.info(`Navigation to login rejected: ${r}`);
        this.auth.logout();
      }
    );
  }
}
