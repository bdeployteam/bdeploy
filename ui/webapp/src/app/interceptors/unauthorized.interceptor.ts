import { Injectable } from '@angular/core';
import {
  HttpInterceptor, HttpHandler, HttpRequest
} from '@angular/common/http';
import { AuthenticationService } from '../services/authentication.service';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { LoggingService, Logger } from '../services/logging.service';

@Injectable()
export class UnauthorizedInterceptor implements HttpInterceptor {

  log: Logger = this.loggingService.getLogger('UnauthorizedInterceptor');

  constructor(private auth: AuthenticationService, private loggingService: LoggingService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler) {
    return next.handle(req).pipe(catchError(err => {
      if (err.status === 401) {
        // API request unauthorized, log out the application
        this.log.debug('unauthorized request: ' + err.url);
        this.auth.logout();
      }
      return throwError(err);
    }));
  }
}
