import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthenticationService } from '../services/authentication.service';

@Injectable({
  providedIn: 'root',
})
export class AdminGuard {
  private authService = inject(AuthenticationService);
  private snackbar = inject(MatSnackBar);
  private router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    if (this.authService.isGlobalAdmin()) {
      return true;
    }
    this.snackbar.open(
      `Unfortunately, ${route.url.join(
        '/'
      )} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`,
      'DISMISS',
      { panelClass: 'error-snackbar' }
    );
    this.router.navigate(['/groups/browser']);
    return false;
  }
}
