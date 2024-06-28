import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { AuthenticationService } from '../services/authentication.service';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard {
  private readonly authService = inject(AuthenticationService);
  private readonly router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    // try some current-state check to determine whether we need to redirect.
    if (!this.authService.getToken()) {
      this.router.navigate(['/login'], {
        queryParams: { returnUrl: state.url },
      });
    }

    return this.authService.isAuthenticated();
  }
}
