import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanDeactivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, of } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { BdDialogComponent } from '../components/bd-dialog/bd-dialog.component';
import { LoggingService } from '../services/logging.service';
import { NavAreasService } from '../services/nav-areas.service';

/**
 * A dialog which may be dirty. Navigation if dirty will trigger a confirmation.
 *
 * Note: There are three requirements for proper dirty handling:
 *  1) Implement the DirtyableDialog interface.
 *  2) Register the component in the constructur with NavAreasService#registerDirtyable.
 *  3) Add the DirtyDialogGuard to canDeactivate of the components route.
 */
export interface DirtyableDialog {
  dialog: BdDialogComponent;
  isDirty(): boolean;
}

@Injectable({
  providedIn: 'root',
})
export class DirtyDialogGuard implements CanDeactivate<DirtyableDialog> {
  private log = this.logging.getLogger('DirtyDialogGuard');

  constructor(private logging: LoggingService, private areas: NavAreasService, private router: Router) {}

  canDeactivate(
    component: DirtyableDialog,
    currentRoute: ActivatedRouteSnapshot,
    currentState: RouterStateSnapshot,
    nextState?: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    const ignore = this.router.getCurrentNavigation()?.extras?.state?.ignoreDirtyGuard;
    if (!!ignore) {
      return true; // forced navigation.
    }

    // If there is an open *and* dirty panel, special handling is required to keep popups
    // in visible areas (maximized panel).
    if (this.areas.hasDirtyPanel() && this.areas.getDirtyableType(component) !== 'panel') {
      // 1. confirm on panel - otherwise reject
      // 2. hide panel
      // 3. confirm on primary - otherwise reject
      // 4. acutally close panel (discard data)

      return this.confirm(this.areas.getDirtyable('panel')).pipe(
        switchMap((r) => {
          if (!r) {
            return of(false);
          } else {
            // hide it *directly*, not via navigation. a nested navigation would cancel the current one.
            // to be able to see the prompt when the panel was maximised, we need to hide the panel.
            this.areas.panelVisible$.next(false);

            // ask confirmation on the actual component (the primary one in this case).
            return this.confirm(component).pipe(
              tap((x) => {
                if (x) {
                  // navigation accepted. since the panel is only hidden, we need to make sure it is completely closed
                  // once the navigation is done.
                  this.areas.forcePanelClose$.next(true);
                } else {
                  // navigation is cancelled. we *instead* close the panel fully, since it is only hidden now.
                  this.areas.closePanel(true);
                }
              })
            );
          }
        })
      );
    }

    if (!component.isDirty()) {
      return true;
    }

    return this.confirm(component);
  }

  private confirm(component: DirtyableDialog) {
    return component.dialog.confirm('Unsaved Changes', 'The dialog contains unsaved changes. Are you sure you want to leave the dialog?', 'save').pipe(
      tap((result) => {
        if (result) {
          this.log.info('User confirmed discarding pending changes.');
        }
      })
    );
  }
}
