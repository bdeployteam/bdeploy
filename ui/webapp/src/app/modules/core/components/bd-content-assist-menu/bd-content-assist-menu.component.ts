import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, ElementRef, Input, TemplateRef, ViewChild, ViewContainerRef, inject } from '@angular/core';

export class ContentCompletion {
  value: string;
  icon: string;
  description: string;
}

@Component({
  selector: 'app-bd-content-assist-menu',
  templateUrl: './bd-content-assist-menu.component.html',
  styleUrls: ['./bd-content-assist-menu.component.css'],
})
export class BdContentAssistMenuComponent {
  private overlay = inject(Overlay);
  private viewContainerRef = inject(ViewContainerRef);

  @Input() attachTo: HTMLElement;
  @Input() values: ContentCompletion[];
  @Input() prefixes: ContentCompletion[];

  public onClickSelect: (s) => void;

  protected matches: ContentCompletion[];
  protected selected: number;
  protected isPrefix = true;
  protected tooManyMatches = false;

  @ViewChild('menu') private menu: TemplateRef<any>;
  @ViewChild('itemContainer', { static: false, read: ElementRef })
  private containerElement: ElementRef;
  private overlayRef: OverlayRef;

  public show(word: string) {
    if (!this.values?.length) {
      return; // nothing to do.
    }

    // if the word contains the same amount of {{ and }}, we are not needed.
    if ((word?.match(/{{/g) || []).length === (word?.match(/}}/g) || []).length) {
      // same amount.
      this.hide();
      return;
    }

    this.tooManyMatches = false;
    this.isPrefix = false;
    if (this.prefixes?.length) {
      const prefixes = this.prefixes.filter((p) => word.startsWith(p.value));

      // in case we have exactly one prefix, we can continue with values. otherwise select a prefix first.
      if (prefixes.length !== 1) {
        this.isPrefix = true;
        // other way round, select potential prefixes.
        this.matches = this.prefixes.filter((p) => p.value.startsWith(word));
      }
    }

    if (!this.isPrefix) {
      this.matches = this.values.filter((v) => v.value.startsWith(word));
    }

    if (!this.matches?.length) {
      this.hide();
      return;
    }

    if (this.matches?.length > 100) {
      this.matches = this.matches.slice(0, 100);
      this.tooManyMatches = true;
    }

    // reset on changes
    this.selected = 0;

    if (this.overlayRef) {
      return; // already visible.
    }

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(this.attachTo)
        .withPositions([
          {
            originX: 'end',
            originY: 'bottom',
            overlayX: 'end',
            overlayY: 'top',
            offsetY: -17,
            offsetX: -10,
          },
          {
            originX: 'end',
            originY: 'top',
            overlayX: 'end',
            overlayY: 'bottom',
            offsetY: -5,
            offsetX: -10,
          },
        ])
        .withPush(false)
        .withViewportMargin(10),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: false,
      disposeOnNavigation: true,
    });
    const portal = new TemplatePortal(this.menu, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  public hide() {
    this.matches = [];
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

  public isVisible() {
    return !!this.overlayRef;
  }

  public next() {
    this.setSelected((this.selected + 1) % this.matches.length);
  }

  public previous() {
    this.setSelected((this.selected - 1 + this.matches.length) % this.matches.length);
  }

  public select(): string {
    const result = this.matches[this.selected];

    if (this.isPrefix) {
      this.show(result.value);
    } else {
      this.hide();
    }

    return result.value;
  }

  protected setSelected(i: number) {
    this.selected = i;

    // container may only contain options, so index access works.
    this.containerElement.nativeElement?.children[i]?.scrollIntoView({
      block: 'end',
      behavior: 'smooth',
    });
  }
}
