import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthenticationInterceptor } from './authentication.interceptor';
import { HttpErrorHandlerInterceptor } from './error-handler.interceptor';
import { UnauthorizedInterceptor } from './unauthorized.interceptor';

export const httpInterceptorProviders = [
  { provide: HTTP_INTERCEPTORS, useClass: AuthenticationInterceptor, multi: true },
  { provide: HTTP_INTERCEPTORS, useClass: UnauthorizedInterceptor, multi: true },
  { provide: HTTP_INTERCEPTORS, useClass: HttpErrorHandlerInterceptor, multi: true },
];
