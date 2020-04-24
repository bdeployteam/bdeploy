import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { NO_ERROR_HANDLING_HDR } from '../../../models/consts';
import { ErrorMessage, Logger, LoggingService } from '../services/logging.service';
import { SystemService } from '../services/system.service';

@Injectable()
export class HttpErrorHandlerInterceptor implements HttpInterceptor {

  private log: Logger = this.loggingService.getLogger('HttpErrorHandlerInterceptor');

  constructor(
    private loggingService: LoggingService,
    private systemService: SystemService,
    private snackbar: MatSnackBar,
    private router: Router
  ) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(request).pipe(catchError(e => {
      if (e instanceof HttpErrorResponse && e.status === 403) {
        this.snackbar.open(`Unfortunately, /${e.url} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`, 'DISMISS', { panelClass: 'error-snackbar' });
        this.router.navigate(['/instancegroup/browser']);
        return of(null);
    } else if (e instanceof HttpErrorResponse && e.status !== 401 && !request.headers.has(NO_ERROR_HANDLING_HDR)) {
      // let 401 pass through for logout redirection in the other interceptor :)
      if (e.status === 0) {
          this.systemService.backendUnreachable();
        } else {
          let displayPath = request.url;
          try {
            displayPath = new URL(request.url).pathname;
          } catch (error) {
            this.log.warn(new ErrorMessage('Cannot parse request URL', error));
          }
          this.log.errorWithGuiMessage(new ErrorMessage(e.status + ': ' + e.statusText + ': ' + displayPath, e));
        }
        return of(null);
      }
      return throwError(e);
    }));
  }

}
