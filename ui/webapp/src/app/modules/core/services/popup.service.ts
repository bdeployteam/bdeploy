import { OverlayRef } from '@angular/cdk/overlay';
import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class PopupService {
  public reset$ = new Subject<OverlayRef>();
  private overlayRef: { trigger: OverlayRef; click: OverlayRef } = {
    trigger: null,
    click: null,
  };

  public setOverlay(value: OverlayRef, trigger: 'click' | 'hover'): void {
    this.overlayRef[trigger] = value;
    this.reset$.next(value);
  }

  public getOverlay(trigger: 'click' | 'hover'): OverlayRef {
    return this.overlayRef[trigger];
  }
}
