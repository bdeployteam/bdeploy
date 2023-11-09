import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { DelayForAuthInterceptor } from './delay-for-auth.interceptor';
import { HttpErrorHandlerInterceptor } from './error-handler.interceptor';
import { NoLoadingBarInterceptor } from './no-loading-bar.interceptor';
import { UnauthorizedInterceptor } from './unauthorized.interceptor';

export const httpInterceptorProviders = [
  {
    provide: HTTP_INTERCEPTORS,
    useClass: DelayForAuthInterceptor,
    multi: true,
  },
  {
    provide: HTTP_INTERCEPTORS,
    useClass: UnauthorizedInterceptor,
    multi: true,
  },
  {
    provide: HTTP_INTERCEPTORS,
    useClass: HttpErrorHandlerInterceptor,
    multi: true,
  },
  {
    provide: HTTP_INTERCEPTORS,
    useClass: NoLoadingBarInterceptor,
    multi: true,
  },
];
