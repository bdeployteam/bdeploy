import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanDeactivate, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { BdDialogComponent } from '../components/bd-dialog/bd-dialog.component';
import { LoggingService } from '../services/logging.service';

/** A dialog which may be dirty. Navigation if dirty will trigger a confirmation. */
export interface DirtyableDialog {
  dialog: BdDialogComponent;
  isDirty(): boolean;
}

@Injectable({
  providedIn: 'root',
})
export class DirtyDialogGuard implements CanDeactivate<DirtyableDialog> {
  private log = this.logging.getLogger('DirtyDialogGuard');

  constructor(private logging: LoggingService) {}

  canDeactivate(
    component: DirtyableDialog,
    currentRoute: ActivatedRouteSnapshot,
    currentState: RouterStateSnapshot,
    nextState?: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    if (!component.isDirty()) {
      return true;
    }
    return component.dialog.confirm('Unsaved Changes', 'The dialog contains unsaved changes. Are you sure you want to leave the dialog?', 'save').pipe(
      tap((result) => {
        if (result) {
          this.log.info('User confirmed discarding pending changes.');
        }
      })
    );
  }
}
