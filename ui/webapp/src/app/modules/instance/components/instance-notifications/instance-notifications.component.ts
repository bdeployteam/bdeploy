import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, Input, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { MatButton } from '@angular/material';

export enum Severity {
  INFO, WARNING, ERROR
}

export class InstanceNotification {
  template: TemplateRef<any>;
  severity: Severity;
  priority: number;
}

@Component({
  selector: 'app-instance-notifications',
  templateUrl: './instance-notifications.component.html',
  styleUrls: ['./instance-notifications.component.css']
})
export class InstanceNotificationsComponent implements OnInit {

  @Input()
  notifications: InstanceNotification[];

  private overlayRef: OverlayRef;

  constructor(
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef
  ) { }

  ngOnInit() {
  }

  getHighestSeverity() {
    let highest;

    for (const notification of this.notifications) {
      if (!highest) {
        highest = notification.severity;
      }

      if (notification.severity > highest) {
        highest = notification.severity;
      }
    }

    return highest;
  }

  isInfo() {
    return this.getHighestSeverity() === Severity.INFO;
  }

  isWarning() {
    return this.getHighestSeverity() === Severity.WARNING;
  }

  isError() {
    return this.getHighestSeverity() === Severity.ERROR;
  }

  isEmpty() {
    return !this.notifications || this.notifications.length <= 0;
  }

  openOverlay(relative: MatButton, template: TemplateRef<any>) {

    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay.position().flexibleConnectedTo(relative._elementRef)
        .withPositions([{
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

  public closeOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

}
