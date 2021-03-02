import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { Injectable } from '@angular/core';
import { delay, retryWhen } from 'rxjs/operators';
import { ConnectionLostComponent } from '../components/connection-lost/connection-lost.component';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root',
})
export class SystemService {
  private recovering = false;
  private overlayRef: OverlayRef;

  constructor(private configService: ConfigService, private overlay: Overlay) {}

  public backendUnreachable(): void {
    if (!this.recovering) {
      this.recovering = true;

      this.overlayRef = this.overlay.create({
        positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
        hasBackdrop: true,
      });

      const portal = new ComponentPortal(ConnectionLostComponent);
      this.overlayRef.attach(portal);

      this.configService
        .tryGetBackendInfo()
        .pipe(retryWhen((errors) => errors.pipe(delay(2000))))
        .subscribe((r) => {
          this.closeOverlay();
          this.recovering = false;

          if (!this.configService.config) {
            window.location.reload();
          }
        });
    }
  }

  /** Closes the overlay if present */
  closeOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }
}
