import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { NO_ERROR_HANDLING_HDR } from 'src/app/models/consts';
import { AuthenticationService } from '../services/authentication.service';

@Injectable()
export class UnauthorizedInterceptor implements HttpInterceptor {
  private auth = inject(AuthenticationService);
  private router = inject(Router);

  intercept(req: HttpRequest<unknown>, next: HttpHandler) {
    return next.handle(req).pipe(
      catchError((err) => {
        if (err.status === 401 && !req.headers.has(NO_ERROR_HANDLING_HDR)) {
          // API request unauthorized, log out the application
          this.logout();
          return of(null);
        }
        return throwError(() => err);
      }),
    );
  }

  logout(): void {
    this.auth.logout().subscribe(() => {
      this.router.navigate(['/login']).then(() => console.log('user logged out'));
    });
  }
}
