import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-theme-chooser',
  templateUrl: './theme-chooser.component.html',
  styleUrls: ['./theme-chooser.component.css'],
})
export class ThemeChooserComponent implements OnInit {
  private overlayRef: OverlayRef;

  constructor(
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    public themeService: ThemeService
  ) {}

  ngOnInit() {}

  /** Opens a modal overlay popup showing the given template */
  openOverlay(relative: HTMLElement, template: TemplateRef<any>) {
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(relative)
        .withPositions([
          {
            overlayX: 'end',
            overlayY: 'top',
            originX: 'end',
            originY: 'bottom',
            offsetY: 5,
          },
        ]),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: true,
      backdropClass: 'chooser-backdrop',
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
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
