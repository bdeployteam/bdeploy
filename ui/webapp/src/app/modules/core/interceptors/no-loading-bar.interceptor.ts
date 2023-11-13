import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { NGX_LOADING_BAR_IGNORED } from '@ngx-loading-bar/http-client';
import { finalize } from 'rxjs';

let cnt = 0;

window.addEventListener('beforeunload', (event) => {
  if (cnt > 0) {
    event.preventDefault();
    event.returnValue = true;
  }
});

@Injectable()
export class NoLoadingBarInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler) {
    const noLoadingBar = req.context.get(NGX_LOADING_BAR_IGNORED);
    if (noLoadingBar) {
      cnt++;
    }
    return next.handle(req).pipe(
      finalize(() => {
        if (noLoadingBar) {
          cnt--;
        }
      })
    );
  }
}
