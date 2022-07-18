import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthenticationService } from '../services/authentication.service';
import { NavAreasService } from '../services/nav-areas.service';
import { findParam } from './scoped-read.guard';

@Injectable({
  providedIn: 'root',
})
export class ScopedAdminGuard implements CanActivate {
  constructor(
    private authService: AuthenticationService,
    private snackbar: MatSnackBar,
    private router: Router,
    private areas: NavAreasService
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean | Observable<boolean> {
    const group =
      findParam('group', route) ||
      this.areas._tempNavGroupContext$.value ||
      this.areas.groupContext$.value;
    const repo =
      findParam('repo', route) ||
      this.areas._tempNavGroupContext$.value ||
      this.areas.repositoryContext$.value;
    const ctx = group ? group : repo;

    this.areas._tempNavGroupContext$.next(group);
    this.areas._tempNavRepoContext$.next(group);

    if (!this.authService.isAuthenticated()) {
      return true;
    }

    return this.authService.isScopedAdmin$(ctx).pipe(
      map((isScopedAdmin) => {
        if (isScopedAdmin) {
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
      })
    );
  }
}
