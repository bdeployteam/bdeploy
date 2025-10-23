import { Platform } from '@angular/cdk/platform';
import { inject, Injectable, NgZone } from '@angular/core';
import { BehaviorSubject, distinctUntilChanged, Observable } from 'rxjs';

export interface ClipboardData {
  data: string;
  error: string;
}

@Injectable({
  providedIn: 'root',
})
export class ClipboardService {
  private readonly ngZone = inject(NgZone);
  private readonly platform = inject(Platform);

  private readonly _clipboard$ = new BehaviorSubject<ClipboardData>({ data: null, error: null });
  public readonly clipboard$: Observable<ClipboardData> = this._clipboard$.pipe(
    distinctUntilChanged((a, b) => a.data === b.data && a.error === b.error)
  );

  constructor() {
    // need to skip this for firefox. They implemented the API, but it causes troubles.
    // currently, firefox can ONLY do this in browser extensions, not websites.
    if (this.platform.FIREFOX) {
      this._clipboard$.next({
        data: null,
        error: 'Clipboard access is not supported on Firefox!',
      });
    } else if (!navigator.clipboard.readText) {
      this._clipboard$.next({
        data: null,
        error: 'Clipboard access is not supported on this browser!',
      });
    } else {
      this.ngZone.runOutsideAngular(() => {
        setInterval(() => this.readFromClipboard(), 1000);
      });
    }

    this.ngZone.runOutsideAngular(() => {
      this.clipboard$.subscribe((cb) => {
        if (cb.error) {
          console.warn(cb.error);
        }
      });
    });
  }

  private readFromClipboard() {
    const perm = 'clipboard-read' as PermissionName; // required due to TS bug.
    navigator.permissions.query({ name: perm }).then(
      (value: PermissionStatus) => {
        if (value.state === 'denied') {
          this._clipboard$.next({
            data: null,
            error: 'No permission to read from the clipboard.',
          });
        }
      },
      (error) => {
        this._clipboard$.next({ data: null, error: `Cannot check clipboard permission (${error}).` });
      }
    );

    navigator.clipboard.readText().then(
      (data) => {
        this._clipboard$.next({ data, error: null });
      },
      () => {
        this._clipboard$.next({ data: null, error: 'Unable to read from clipboard.' });
      }
    );
  }
}
