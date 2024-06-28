import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { skipWhile, switchMap, take } from 'rxjs/operators';
import { NO_UNAUTH_DELAY_HDR } from 'src/app/models/consts';
import { AuthenticationService } from '../services/authentication.service';

@Injectable()
export class DelayForAuthInterceptor implements HttpInterceptor {
  private readonly auth = inject(AuthenticationService);

  intercept(req: HttpRequest<unknown>, next: HttpHandler) {
    // only continue with requests if we have a user already.
    return this.auth.getTokenSubject().pipe(
      skipWhile(
        (u) =>
          !u && // as long as there is no user
          !req.headers.has(NO_UNAUTH_DELAY_HDR) && // or we're in the process of logging in
          req.url.includes('/api/'), // or we're loading resources and not trying to talk to the api.
      ),
      take(1),
      switchMap(() => next.handle(req)),
    );
  }
}
