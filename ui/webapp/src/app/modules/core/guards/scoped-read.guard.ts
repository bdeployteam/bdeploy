import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { AuthenticationService } from '../services/authentication.service';
import { NavAreasService } from '../services/nav-areas.service';

// common logic to find a named param in a route tree.
export function findParam(name: string, route: ActivatedRouteSnapshot): string {
  const param = route.paramMap.get(name);
  if (param) {
    return param;
  }

  for (const child of route.children) {
    const tParam = findParam(name, child);
    if (tParam) {
      return tParam;
    }
  }
}

@Injectable({
  providedIn: 'root',
})
export class ScopedReadGuard implements CanActivate {
  constructor(
    private authService: AuthenticationService,
    private snackbar: MatSnackBar,
    private router: Router,
    private areas: NavAreasService
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
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

    if (
      this.authService.isAuthenticated() &&
      !this.authService.isScopedRead(ctx)
    ) {
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
    return true;
  }
}
