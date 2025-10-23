import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { NO_ERROR_HANDLING_HDR } from 'src/app/models/consts';
import { ConfigService } from '../services/config.service';
import { NavAreasService } from '../services/nav-areas.service';
import he from 'he';

@Injectable()
export class HttpErrorHandlerInterceptor implements HttpInterceptor {
  private readonly config = inject(ConfigService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly areas = inject(NavAreasService);

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    let displayPath = '';
    let msg = '';
    return next.handle(request).pipe(
      catchError((e) => {
        if (e instanceof HttpErrorResponse && !request.headers.has(NO_ERROR_HANDLING_HDR)) {
          switch (e.status) {
            case 0:
              this.config.markServerOffline();
              return throwError(() => e);
            case 401:
              // let 401 pass through for logout redirection in the other interceptor :)
              break;
            case 403:
            /* falls through */
            case 404:
              msg = `Unfortunately, /${e.url} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`;
              this.snackbar.open(msg, 'DISMISS', {
                panelClass: 'error-snackbar',
              });
              this.areas.forcePanelClose$.next(true);
              this.router.navigate(['/groups/browser'], {
                state: { ignoreDirtyGuard: true },
              });
              return throwError(() => e);
            case 499:
              // special version mismatch code.
              this.snackbar.open(e.statusText, 'DISMISS', {
                panelClass: 'error-snackbar',
              });
              break;
            default:
              displayPath = request.url;
              try {
                displayPath = new URL(request.url).pathname;
              } catch (error) {
                // silent.
              }
              // response status texts are HTML encoded, so we need to decode that here manually.
              this.snackbar.open(e.status + ': ' + he.decode(e.statusText) + ': ' + displayPath, 'DISMISS', {
                panelClass: 'error-snackbar',
              });
              return throwError(() => e);
          }
        }
        return throwError(() => e);
      })
    );
  }
}
