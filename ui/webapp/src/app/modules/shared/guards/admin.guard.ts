import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthenticationService } from '../../core/services/authentication.service';

@Injectable({
  providedIn: 'root'
})
export class AdminGuard implements CanActivate {

  constructor(private authService: AuthenticationService, private snackbar: MatSnackBar, private router: Router) { }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (!this.authService.isGlobalAdmin()) {
      this.snackbar.open(`Because of missing privileges for /${route.url.join('/')}, we redirected you to the homepage.`, 'DISMISS', { panelClass: 'error-snackbar' });
      this.router.navigate(['/instancegroup/browser']);
      return false;
    }
    return true;
  }
}
