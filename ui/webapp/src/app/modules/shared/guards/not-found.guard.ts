import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class NotFoundGuard implements CanActivate {

  constructor(private snackbar: MatSnackBar, private router: Router) { }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    this.snackbar.open(`Unfortunately, /${route.url.join('/')} was not found, we returned you to the safe-zone.`, 'DISMISS', { panelClass: 'error-snackbar' });
    this.router.navigate(['/instancegroup/browser']);

    return false; // never allow, we redirected right away above.
  }
}
