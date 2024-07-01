import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { NavAreasService } from '../services/nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class NotFoundGuard {
  private readonly snackbar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly areas = inject(NavAreasService);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    this.snackbar.open(
      `Unfortunately, ${route.url.join('/')} was not found, we returned you to the safe-zone.`,
      'DISMISS',
      { panelClass: 'error-snackbar' },
    );
    this.areas.forcePanelClose$.next(true);
    this.router.navigate(['/groups/browser'], {
      state: { ignoreDirtyGuard: true },
    });

    return false; // never allow, we redirected right away above.
  }
}
