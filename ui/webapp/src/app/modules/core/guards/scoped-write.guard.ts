import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthenticationService } from '../services/authentication.service';
import { NavAreasService } from '../services/nav-areas.service';
import { findParam } from './scoped-read.guard';

@Injectable({
  providedIn: 'root',
})
export class ScopedWriteGuard {
  private authService = inject(AuthenticationService);
  private snackbar = inject(MatSnackBar);
  private router = inject(Router);
  private areas = inject(NavAreasService);

  canActivate(route: ActivatedRouteSnapshot): boolean | Observable<boolean> {
    const group = findParam('group', route) || this.areas._tempNavGroupContext$.value || this.areas.groupContext$.value;
    const repo =
      findParam('repo', route) || this.areas._tempNavGroupContext$.value || this.areas.repositoryContext$.value;
    const ctx = group ? group : repo;

    this.areas._tempNavGroupContext$.next(group);
    this.areas._tempNavRepoContext$.next(group);

    return this.authService.isScopedWrite$(ctx).pipe(
      map((isScopedWrite) => {
        if (isScopedWrite) {
          return true;
        }
        this.snackbar.open(
          `Unfortunately, ${route.url.join(
            '/',
          )} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`,
          'DISMISS',
          { panelClass: 'error-snackbar' },
        );
        this.router.navigate(['/groups/browser']);
        return false;
      }),
    );
  }
}
