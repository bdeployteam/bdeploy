import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AuthenticationService } from '../services/authentication.service';

@Injectable()
export class AuthenticationInterceptor implements HttpInterceptor {
  constructor(private auth: AuthenticationService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler) {
    // Get the auth token from the service.
    const authToken = this.auth.getToken();

    if (!authToken) {
      return next.handle(req);
    }

    // Clone the request and set the new header in one step.
    const authReq = req.clone({ setHeaders: { Authorization: 'Bearer ' + authToken } });

    // send cloned request with header to the next handler.
    return next.handle(authReq);
  }
}
