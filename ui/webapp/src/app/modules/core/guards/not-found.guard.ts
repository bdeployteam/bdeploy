import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { NavAreasService } from '../services/nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class NotFoundGuard  {
  constructor(
    private snackbar: MatSnackBar,
    private router: Router,
    private areas: NavAreasService
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    this.snackbar.open(
      `Unfortunately, ${route.url.join(
        '/'
      )} was not found, we returned you to the safe-zone.`,
      'DISMISS',
      { panelClass: 'error-snackbar' }
    );
    this.areas.forcePanelClose$.next(true);
    this.router.navigate(['/groups/browser'], {
      state: { ignoreDirtyGuard: true },
    });

    return false; // never allow, we redirected right away above.
  }
}
