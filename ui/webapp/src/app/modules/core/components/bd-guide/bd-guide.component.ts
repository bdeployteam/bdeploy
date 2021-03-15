import { ConnectedPosition, Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { AfterViewInit, Component, ElementRef, OnInit, TemplateRef, ViewChild, ViewContainerRef } from '@angular/core';
import { GuidedElementRef, GuideService } from '../../services/guide.service';

const LEFT: ConnectedPosition = {
  originX: 'start',
  originY: 'center',
  overlayX: 'end',
  overlayY: 'center',
  offsetX: -15,
};

const RIGHT: ConnectedPosition = {
  originX: 'end',
  originY: 'center',
  overlayX: 'start',
  overlayY: 'center',
  offsetX: 15,
};

const CENTER: ConnectedPosition = {
  originX: 'center',
  originY: 'center',
  overlayX: 'center',
  overlayY: 'center',
};

@Component({
  selector: 'app-bd-guide',
  templateUrl: './bd-guide.component.html',
  styleUrls: ['./bd-guide.component.css'],
})
export class BdGuideComponent implements OnInit, AfterViewInit {
  /* template */ element: GuidedElementRef;

  @ViewChild(TemplateRef) template: TemplateRef<any>;
  @ViewChild('highlight', { static: false }) highlight: ElementRef;

  private overlayRef: OverlayRef;

  constructor(public guides: GuideService, private overlay: Overlay, private viewContainerRef: ViewContainerRef) {}

  ngOnInit(): void {}

  ngAfterViewInit(): void {
    this.guides.element$.subscribe((ele) => {
      setTimeout(() => {
        this.element = ele;
        setTimeout(() => this.update());
      });
    });
  }

  onNext() {
    this.guides.next();
  }

  onSkip() {
    this.guides.skip();
  }

  onDisable() {
    this.guides.disable();
  }

  update() {
    if (!!this.element) {
      this.openOverlay();
    } else {
      this.closeOverlay();
    }
  }

  /** Opens a modal overlay popup showing the given template */
  openOverlay() {
    this.closeOverlay();

    const toHighlight = this.element.element.element;
    console.log(toHighlight);

    if (!!toHighlight) {
      const clientRect = toHighlight.nativeElement.getBoundingClientRect();
      this.highlight.nativeElement.style.width = `${clientRect.width}px`;
      this.highlight.nativeElement.style.height = `${clientRect.height}px`;
      this.highlight.nativeElement.style.top = `${clientRect.top}px`;
      this.highlight.nativeElement.style.left = `${clientRect.left}px`;
      this.highlight.nativeElement.style.display = 'block';
    } else {
      this.highlight.nativeElement.style.display = 'none';
    }

    this.overlayRef = this.overlay.create({
      positionStrategy: !!toHighlight
        ? this.overlay.position().flexibleConnectedTo(toHighlight).withPositions([LEFT, RIGHT, CENTER]).withPush(false)
        : this.overlay.position().global().centerHorizontally().centerVertically(),
      hasBackdrop: true,
      backdropClass: !!toHighlight ? 'bd-no-backdrop' : 'bd-strong-backdrop',
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.onNext());

    const portal = new TemplatePortal(this.template, this.viewContainerRef);
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
