import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { BdDialogMessageAction } from '../components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from '../components/bd-dialog/bd-dialog.component';
import { NavAreasService } from '../services/nav-areas.service';

enum DirtyActionType {
  CANCEL = 'CANCEL',
  DISCARD = 'DISCARD',
  SAVE = 'SAVE',
}

const actCancel: BdDialogMessageAction<DirtyActionType> = {
  name: 'Stay',
  result: DirtyActionType.CANCEL,
  confirm: true
};

const actDiscard: BdDialogMessageAction<DirtyActionType> = {
  name: 'Discard',
  result: DirtyActionType.DISCARD,
  confirm: false
};

const actSave: BdDialogMessageAction<DirtyActionType> = {
  name: 'Save',
  result: DirtyActionType.SAVE,
  confirm: false
};

/**
 * A dialog which may be dirty. Navigation if dirty will trigger a confirmation.
 *
 * Note: There are three requirements for proper dirty handling:
 *  1) Implement the DirtyableDialog interface.
 *  2) Register the component in the constructor with NavAreasService#registerDirtyable.
 *  3) Add the DirtyDialogGuard to canDeactivate of the components route.
 */
export interface DirtyableDialog {
  /** The dialog with dirty handling, used to issue confirmations */
  dialog: BdDialogComponent;

  /** Determines whether the dialog is dirty. */
  isDirty(): boolean;

  /** Determines whether the dialog should have save button. */
  canSave?(): boolean;

  /**
   * Saves the current state - may NOT perform ANY navigation!
   * <p>
   * If observable from dirty side panel resolves to boolean 'false' value, then panel will not be closed
   * and navigation will be cancelled.
   */
  doSave(): Observable<unknown | false>;
}

@Injectable({
  providedIn: 'root'
})
export class DirtyDialogGuard {
  private readonly areas = inject(NavAreasService);
  private readonly router = inject(Router);

  canDeactivate(component: DirtyableDialog): Observable<boolean> {
    const ignore = this.router.currentNavigation()?.extras?.state?.['ignoreDirtyGuard'];
    if (ignore) {
      return of(true); // forced navigation.
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
          if (r === DirtyActionType.CANCEL) {
            return of(false);
          } else {
            // hide it *directly*, not via navigation. a nested navigation would cancel the current one.
            // to be able to see the prompt when the panel was maximised, we need to hide the panel.
            this.areas.panelVisible$.next(false);

            let panelSave: Observable<unknown> = of(true);
            if (r === DirtyActionType.SAVE) {
              // need to save!
              panelSave = this.areas.getDirtyable('panel').doSave();
            }

            // if the primary dialog is not dirtyable, continue.
            if (!this.areas.getDirtyableType(component)) {
              this.areas.forcePanelClose$.next(true);
              return panelSave.pipe(switchMap(() => of(true))); // disregard the result, can be any.
            }

            // ask confirmation on the actual component (the primary one in this case).
            return this.confirmAndSavePrimaryComponent(panelSave, component);
          }
        })
      );
    }

    // panel exists, is dirtyable, is NOT dirty, and main component is not the panel - we want to force the panel to close.
    if (!!this.areas.getDirtyable('panel') && this.areas.getDirtyableType(component) !== 'panel') {
      // hide the panel right away to be out of the way for a potential dirty check on the main component.
      this.areas.panelVisible$.next(false);
      this.areas.forcePanelClose$.next(true);
    }

    if (this.areas.getDirtyableType(component)) {
      if (!component.isDirty()) {
        return of(true);
      }

      return this.confirmAndSaveComponent(component);
    } else {
      return of(true);
    }
  }

  private confirmAndSaveComponent(component: DirtyableDialog): Observable<boolean> {
    return this.confirm(component).pipe(
      switchMap((x) => {
        if (x === DirtyActionType.CANCEL) {
          this.areas.forcePanelClose$.next(false);
          return of(false);
        }
        if (x === DirtyActionType.SAVE) {
          return component.doSave().pipe(
            switchMap((result) => of (result !== false))
          );
        }
        return of(true);
      })
    );
  }

  private confirmAndSavePrimaryComponent(
    panelSave: Observable<unknown>,
    component: DirtyableDialog
  ): Observable<boolean> {
    return panelSave.pipe(
      switchMap(() =>
        this.confirm(component).pipe(
          switchMap((x) => {
            if (x !== DirtyActionType.CANCEL) {
              // navigation accepted. since the panel is only hidden, we need to make sure it is completely closed
              // once the navigation is done.
              this.areas.forcePanelClose$.next(true);

              let primarySave: Observable<unknown> = of(true);
              if (x === DirtyActionType.SAVE) {
                primarySave = component.doSave();
              }
              return primarySave.pipe(switchMap(() => of(true)));
            } else {
              // navigation is cancelled. we *instead* close the panel fully, since it is only hidden now.
              this.areas.closePanel(true);
              return of(false);
            }
          })
        )
      )
    );
  }

  private confirm(component: DirtyableDialog) {
    const canSave = component.canSave ? component.canSave() : true;
    return component.dialog
      .message({
        header: 'Save Changes?',
        message:
          'The dialog contains unsaved changes. Save the changes before leaving? You may also stay and continue editing.',
        icon: 'save',
        actions: canSave ? [actCancel, actDiscard, actSave] : [actCancel, actDiscard]
      })
      .pipe(
        tap((result) => {
          if (result === DirtyActionType.DISCARD) {
            console.warn('User confirmed discarding pending changes.');
          }
        })
      );
  }
}
