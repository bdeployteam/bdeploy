import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
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
  return null;
}

@Injectable({
  providedIn: 'root'
})
export class ScopedReadGuard {
  private readonly authService = inject(AuthenticationService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly areas = inject(NavAreasService);

  canActivate(route: ActivatedRouteSnapshot): boolean | Observable<boolean> {
    const group = findParam('group', route) || this.areas._tempNavGroupContext$.value || this.areas.groupContext$.value;
    const repo =
      findParam('repo', route) || this.areas._tempNavGroupContext$.value || this.areas.repositoryContext$.value;
    const ctx = group || repo;

    this.areas._tempNavGroupContext$.next(group);
    this.areas._tempNavRepoContext$.next(group);

    return this.authService.isScopedRead$(ctx).pipe(
      map((isScopedRead) => {
        if (isScopedRead) {
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
