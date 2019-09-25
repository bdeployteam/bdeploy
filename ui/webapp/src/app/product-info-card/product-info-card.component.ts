import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, Input, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { MatButton } from '@angular/material';
import { ProductDto } from '../models/gen.dtos';

@Component({
  selector: 'app-product-info-card',
  templateUrl: './product-info-card.component.html',
  styleUrls: ['./product-info-card.component.css']
})
export class ProductInfoCardComponent implements OnInit {

  @Input() public productDto: ProductDto;

  private overlayRef: OverlayRef;

  constructor(
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef

  ) { }

  ngOnInit() {
  }

  public getLabelKeys(): string[] {
    return this.productDto ? Object.keys(this.productDto.labels) : [];
  }

  openOverlay(relative: MatButton, template: TemplateRef<any>) {

    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay.position().flexibleConnectedTo(relative._elementRef)
        .withPositions([{
            overlayX: 'end',
            overlayY: 'bottom',
            originX: 'center',
            originY: 'top',
            offsetX: 35,
            offsetY: -10,
            panelClass: 'info-card'
        }, {
          overlayX: 'end',
          overlayY: 'top',
          originX: 'center',
          originY: 'bottom',
          offsetX: 35,
          offsetY: 10,
          panelClass: 'info-card-below'
        }])
        .withPush(),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: true,
      backdropClass: 'info-backdrop',
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  closeOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

}
