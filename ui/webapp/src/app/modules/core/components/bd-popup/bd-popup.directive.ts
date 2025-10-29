import { ConnectedPosition, Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import {
  Directive,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  Output,
  Renderer2,
  TemplateRef,
  ViewContainerRef,
  inject,
} from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { cloneDeep } from 'lodash-es';
import { PopupService } from '../../services/popup.service';

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
export type PopupPosition =
  | 'below-left'
  | 'below-right'
  | 'above-left'
  | 'above-right'
  | 'left-above'
  | 'left-below'
  | 'right-above'
  | 'right-below';

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
  panelClass: 'bd-popup-panel-below-left',
};
const BELOW_RIGHT: ConnectedPosition = {
  originX: 'start',
  originY: 'bottom',
  overlayX: 'start',
  overlayY: 'top',
  offsetY: 10,
  panelClass: 'bd-popup-panel-below-right',
};
const ABOVE_LEFT: ConnectedPosition = {
  originX: 'end',
  originY: 'top',
  overlayX: 'end',
  overlayY: 'bottom',
  offsetY: -10,
  offsetX: -VIEWPORT_MARGIN,
  panelClass: 'bd-popup-panel-above-left',
};
const ABOVE_RIGHT: ConnectedPosition = {
  originX: 'start',
  originY: 'top',
  overlayX: 'start',
  overlayY: 'bottom',
  offsetY: -10,
  panelClass: 'bd-popup-panel-above-right',
};
const LEFT_ABOVE: ConnectedPosition = {
  originX: 'start',
  originY: 'bottom',
  overlayX: 'end',
  overlayY: 'bottom',
  offsetX: -10 - VIEWPORT_MARGIN,
  panelClass: 'bd-popup-panel-left-above',
};
const LEFT_BELOW: ConnectedPosition = {
  originX: 'start',
  originY: 'top',
  overlayX: 'end',
  overlayY: 'top',
  offsetX: -10 - VIEWPORT_MARGIN,
  panelClass: 'bd-popup-panel-left-below',
};
const RIGHT_ABOVE: ConnectedPosition = {
  originX: 'end',
  originY: 'bottom',
  overlayX: 'start',
  overlayY: 'bottom',
  offsetX: 10,
  panelClass: 'bd-popup-panel-right-above',
};
const RIGHT_BELOW: ConnectedPosition = {
  originX: 'end',
  originY: 'top',
  overlayX: 'start',
  overlayY: 'top',
  offsetX: 10,
  panelClass: 'bd-popup-panel-right-below',
};

@Directive({
  selector: '[appBdPopup]',
  exportAs: 'appBdPopup'
})
export class BdPopupDirective implements OnDestroy {
  private readonly host = inject(ElementRef);
  private readonly overlay = inject(Overlay);
  private readonly viewContainerRef = inject(ViewContainerRef);
  private readonly popupService = inject(PopupService);
  private readonly _render = inject(Renderer2);

  @Input() appBdPopup: TemplateRef<unknown>;
  @Input() appBdPopupTrigger: 'click' | 'hover' = 'click';
  @Input() appBdPopupDelay = 0;

  @Input() appBdPopupPosition: PopupPosition = 'below-left';
  @Input() appBdPopupBackdropClass: string;
  @Input() appBdPopupChevronColor: ThemePalette;

  @Output() appBdPopupOpened = new EventEmitter<BdPopupDirective>();

  private delayTimer: ReturnType<typeof setInterval>;
  private overlayRef: OverlayRef;

  private mouseOverElement = false;
  private mouseOverPopup = false;

  private clearEnterListener: () => void;
  private clearLeaveListener: () => void;

  ngOnDestroy(): void {
    this.closeOverlay();
  }

  @HostListener('mouseenter') onMouseEnter() {
    this.mouseOverElement = true;
    if (
      this.appBdPopupTrigger === 'hover' &&
      !!this.appBdPopup &&
      !this.popupService.hasClickPopup() &&
      !this.popupService.hasContentAssist() // only if no click-overlay or content-assist is open
    ) {
      this.delayTimer = setTimeout(() => {
        this.openOverlay();
      }, this.appBdPopupDelay);
    }
  }

  @HostListener('mouseleave') onMouseLeave() {
    this.mouseOverElement = false;
    if (this.appBdPopupTrigger === 'hover' && !!this.appBdPopup) {
      clearTimeout(this.delayTimer);
    }
    this.checkCloseOnLeave();
  }

  @HostListener('click') onMouseClick() {
    if (this.appBdPopupTrigger !== 'click' || !this.appBdPopup) {
      return;
    }

    if (this.overlayRef) {
      this.closeOverlay();
    } else {
      this.openOverlay();
    }
  }

  /** Opens a modal overlay popup showing the given template */
  public openOverlay() {
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(this.host)
        .withPositions(this.fixupPanelClasses(this.getPositions()))
        .withPush(false)
        .withViewportMargin(VIEWPORT_MARGIN),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: this.appBdPopupTrigger === 'click',
      backdropClass: this.appBdPopupBackdropClass,
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOverlay());

    const portal = new TemplatePortal(this.appBdPopup, this.viewContainerRef);
    this.overlayRef.attach(portal);

    this.hookOverlay();

    if (this.appBdPopupTrigger === 'click') {
      this.popupService.setClickPopup(this);
    }
    this.appBdPopupOpened.emit(this);
  }

  private hookOverlay() {
    this.clearEnterListener = this._render.listen(this.overlayRef.hostElement, 'mouseenter', () => {
      this.mouseOverPopup = true;
    });
    this.clearLeaveListener = this._render.listen(this.overlayRef.hostElement, 'mouseleave', () => {
      this.mouseOverPopup = false;
      this.checkCloseOnLeave();
    });
  }

  private unhookOverlay() {
    if (this.clearEnterListener) {
      this.clearEnterListener();
    }
    if (this.clearLeaveListener) {
      this.clearLeaveListener();
    }
  }

  private getPositions(): ConnectedPosition[] {
    // preferred positions from most to least preferred depending on given most-preferred position.
    // preferred placement has a "primary" and a "secondary" component. The primary determined on which
    // side of the button the popup is attached. The secondary determines which side of the popup is
    // attached to the button.
    // the logic is to always first try to alternate the "secondary" placement, and then the "primary"
    // if there is not enough room to fit it to the preferred position.
    switch (this.appBdPopupPosition) {
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

  private fixupPanelClasses(pos: ConnectedPosition[]) {
    const name = this.appBdPopupChevronColor ? this.appBdPopupChevronColor : 'default';
    const result: ConnectedPosition[] = [];
    pos.forEach((p) => {
      const x: ConnectedPosition = cloneDeep(p);
      x.panelClass = `${x.panelClass}-${name}`;
      result.push(x);
    });
    return result;
  }

  /** Closes the overlay if present */
  public closeOverlay() {
    if (this.overlayRef) {
      this.unhookOverlay();
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;

      if (this.appBdPopupTrigger === 'click') {
        this.popupService.setClickPopup(null);
      }
    }
    this.appBdPopupOpened.emit(null);
  }

  private checkCloseOnLeave() {
    if (this.overlayRef && this.appBdPopupTrigger === 'hover') {
      setTimeout(() => {
        if (this.mouseOverElement || this.mouseOverPopup) {
          return;
        }
        this.closeOverlay();
      }, 100);
    }
  }
}
