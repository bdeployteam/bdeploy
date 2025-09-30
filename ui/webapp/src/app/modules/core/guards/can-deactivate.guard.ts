import { Location } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

/**
 * Interface to be implemented by a component to prevent navigation events. Typically
 * a component will prevent navigation when there are unsaved changes.
 */
export interface CanComponentDeactivate {
  canDeactivate: () => Observable<boolean>;
}

@Injectable({
  providedIn: 'root',
})
export class CanDeactivateGuard {
  private readonly location = inject(Location);
  private readonly router = inject(Router);

  canDeactivate(
    component: CanComponentDeactivate,
    currentRoute: ActivatedRouteSnapshot,
    currentState: RouterStateSnapshot,
    nextState?: RouterStateSnapshot,
  ) {
    if (nextState.url === '/login') {
      return true; // always allow forced logout.
    }

    return component.canDeactivate
      ? component.canDeactivate().pipe(
          tap((allowed) => {
            if (!allowed && this.router.currentNavigation().trigger === 'popstate') {
              // FORWARD navigation is broken by this, but there is no simple and no plausible way to
              // distinguish back vs. forward button (grmpf). In the case where the user presses
              // forward and then cancels due to unsaved changes, we will destroy the forward history
              // by pushing state here.
              const currentUrlTree = this.router.createUrlTree([], currentRoute);
              const currentUrl = currentUrlTree.toString();
              this.location.go(currentUrl);
            }
          }),
        )
      : true;
  }
}
