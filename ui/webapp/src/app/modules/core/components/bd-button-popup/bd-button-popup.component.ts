import { ConnectedPosition, Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, EventEmitter, Input, OnInit, Output, TemplateRef, ViewChild, ViewContainerRef } from '@angular/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { BdButtonComponent } from '../bd-button/bd-button.component';

/**
 * Popup preferred position.
 *
 * "Primary" (first part) dictates on which side of the button the popup will be placed.
 *
 * "Secondary" (second part) dictates the alignment of the popup relative to the button.
 *
 * Example: 'below-left' will place the popup below the button, _then_ align its right side
 * to the right side of the button, so that the popup will *extend* to the left.
 */
export type PopupPosition = 'below-left' | 'below-right' | 'above-left' | 'above-right' | 'left-above' | 'left-below' | 'right-above' | 'right-below';

/**
 * minimum distance to the edge of the viewport in any direction.
 * this needs to be accounted for in position strategies on the X axis.
 */
const VIEWPORT_MARGIN = 10;

const BELOW_LEFT: ConnectedPosition = {
  originX: 'end',
  originY: 'bottom',
  overlayX: 'end',
  overlayY: 'top',
  offsetY: 10,
  offsetX: -VIEWPORT_MARGIN,
  panelClass: 'bd-button-popup-panel-below-left',
};
const BELOW_RIGHT: ConnectedPosition = {
  originX: 'start',
  originY: 'bottom',
  overlayX: 'start',
  overlayY: 'top',
  offsetY: 10,
  panelClass: 'bd-button-popup-panel-below-right',
};
const ABOVE_LEFT: ConnectedPosition = {
  originX: 'end',
  originY: 'top',
  overlayX: 'end',
  overlayY: 'bottom',
  offsetY: -10,
  offsetX: -VIEWPORT_MARGIN,
  panelClass: 'bd-button-popup-panel-above-left',
};
const ABOVE_RIGHT: ConnectedPosition = {
  originX: 'start',
  originY: 'top',
  overlayX: 'start',
  overlayY: 'bottom',
  offsetY: -10,
  panelClass: 'bd-button-popup-panel-above-right',
};
const LEFT_ABOVE: ConnectedPosition = {
  originX: 'start',
  originY: 'bottom',
  overlayX: 'end',
  overlayY: 'bottom',
  offsetX: -10 - VIEWPORT_MARGIN,
  panelClass: 'bd-button-popup-panel-left-above',
};
const LEFT_BELOW: ConnectedPosition = {
  originX: 'start',
  originY: 'top',
  overlayX: 'end',
  overlayY: 'top',
  offsetX: -10 - VIEWPORT_MARGIN,
  panelClass: 'bd-button-popup-panel-left-below',
};
const RIGHT_ABOVE: ConnectedPosition = {
  originX: 'end',
  originY: 'bottom',
  overlayX: 'start',
  overlayY: 'bottom',
  offsetX: 10,
  panelClass: 'bd-button-popup-panel-right-above',
};
const RIGHT_BELOW: ConnectedPosition = {
  originX: 'end',
  originY: 'top',
  overlayX: 'start',
  overlayY: 'top',
  offsetX: 10,
  panelClass: 'bd-button-popup-panel-right-below',
};

@Component({
  selector: 'app-bd-button-popup',
  templateUrl: './bd-button-popup.component.html',
  styleUrls: ['./bd-button-popup.component.css'],
})
export class BdButtonPopupComponent implements OnInit {
  @Input() text: string;
  @Input() icon: string;
  @Input() badge: number;
  @Input() collapsed = true;
  @Input() tooltip: TooltipPosition;
  @Input() preferredPosition: PopupPosition = 'below-left';
  @Input() backdropClass: string;

  @Output() popupOpened = new EventEmitter<BdButtonPopupComponent>();

  @ViewChild('popup') popupTemplate: TemplateRef<any>;
  @ViewChild('button') button: BdButtonComponent;

  private overlayRef: OverlayRef;

  constructor(private overlay: Overlay, private viewContainerRef: ViewContainerRef) {}

  ngOnInit(): void {}

  /** Opens a modal overlay popup showing the given template */
  openOverlay() {
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(this.button._elementRef)
        .withPositions(this.getPositions())
        .withPush(false)
        .withViewportMargin(VIEWPORT_MARGIN),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: true,
      backdropClass: this.backdropClass,
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOverlay());

    const portal = new TemplatePortal(this.popupTemplate, this.viewContainerRef);
    this.overlayRef.attach(portal);

    this.popupOpened.emit(this);
  }

  private getPositions(): ConnectedPosition[] {
    // preferred positions from most to least preferred depending on given most-preferred position.
    // preferred placement has a "primary" and a "secondary" component. The primary determined on which
    // side of the button the popup is attached. The secondary determines which side of the popup is
    // attached to the button.
    // the logic is to always first try to alternate the "secondary" placement, and then the "primary"
    // if there is not enough room to fit it to the preferred position.
    switch (this.preferredPosition) {
      case 'above-left':
        return [ABOVE_LEFT, ABOVE_RIGHT, BELOW_LEFT];
      case 'above-right':
        return [ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT];
      case 'below-left':
        return [BELOW_LEFT, BELOW_RIGHT, ABOVE_LEFT];
      case 'below-right':
        return [BELOW_RIGHT, BELOW_LEFT, ABOVE_RIGHT];
      case 'left-above':
        return [LEFT_ABOVE, LEFT_BELOW, RIGHT_ABOVE];
      case 'left-below':
        return [LEFT_BELOW, LEFT_ABOVE, RIGHT_BELOW];
      case 'right-above':
        return [RIGHT_ABOVE, RIGHT_BELOW, LEFT_ABOVE];
      case 'right-below':
        return [RIGHT_BELOW, RIGHT_ABOVE, LEFT_BELOW];
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
