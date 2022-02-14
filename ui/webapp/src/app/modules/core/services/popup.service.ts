import { OverlayRef } from '@angular/cdk/overlay';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class PopupService {
  private overlayRef: OverlayRef;

  public setOverlay(value: OverlayRef): void {
    this.overlayRef = value;
  }

  public getOverlay(): OverlayRef {
    return this.overlayRef;
  }
}
