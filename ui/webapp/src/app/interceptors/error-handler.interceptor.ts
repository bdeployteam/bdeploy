import { HttpErrorResponse, HttpEvent, HttpHandler, HttpHeaders, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ErrorMessage, Logger, LoggingService } from '../services/logging.service';

@Injectable()
export class HttpErrorHandlerInterceptor implements HttpInterceptor {

  public static NO_ERROR_HANDLING_HDR = 'X-No-Global-Error-Handling';
  private log: Logger = this.loggingService.getLogger('HttpErrorHandlerInterceptor');

  public static suppressGlobalErrorHandling(p: HttpHeaders): HttpHeaders {
    return p.append(HttpErrorHandlerInterceptor.NO_ERROR_HANDLING_HDR, 'true');
  }

  constructor(private loggingService: LoggingService) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(request).pipe(catchError(e => {
      if (e instanceof HttpErrorResponse && !request.headers.has(HttpErrorHandlerInterceptor.NO_ERROR_HANDLING_HDR)) {
        let displayPath = request.url;
        try {
          displayPath = new URL(request.url).pathname;
        } catch (error) {
          this.log.warn(new ErrorMessage('Cannot parse request URL', error));
        }
        this.log.error(new ErrorMessage(e.status + ': ' + e.statusText + ': ' + displayPath, e));
        return of(null);
      }
      return throwError(e);
    }));
  }

}
