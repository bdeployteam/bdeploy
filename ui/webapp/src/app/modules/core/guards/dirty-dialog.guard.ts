import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanDeactivate, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { BdDialogComponent } from '../components/bd-dialog/bd-dialog.component';

/** A dialog which may be dirty. Navigation if dirty will trigger a confirmation. */
export interface DirtyableDialog {
  dialog: BdDialogComponent;
  isDirty(): boolean;
}

@Injectable({
  providedIn: 'root',
})
export class DirtyDialogGuard implements CanDeactivate<DirtyableDialog> {
  canDeactivate(
    component: DirtyableDialog,
    currentRoute: ActivatedRouteSnapshot,
    currentState: RouterStateSnapshot,
    nextState?: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    if (!component.isDirty()) {
      return true;
    }
    return component.dialog.confirm('Unsaved Changes', 'The dialog contains unsaved changes. Are you sure you want to leave the dialog?', 'save');
  }
}
