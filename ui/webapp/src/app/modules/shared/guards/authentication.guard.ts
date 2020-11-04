import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthenticationService } from '../../core/services/authentication.service';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard implements CanActivate {
  constructor(private router: Router, private authService: AuthenticationService) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    // try some current-state check to determine whether we need to redirect.
    if (!this.authService.getToken()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    }

    return this.authService.isAuthenticated();
  }
}
