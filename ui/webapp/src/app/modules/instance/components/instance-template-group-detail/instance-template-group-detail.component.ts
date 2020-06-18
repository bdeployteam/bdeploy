import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, Input, IterableDiffers, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { ApplicationType, InstanceTemplateApplication, InstanceTemplateGroup, ProductDto } from 'src/app/models/gen.dtos';
import { ProcessConfigDto } from 'src/app/models/process.model';
import { ApplyMessage } from '../instance-template/instance-template.component';

@Component({
  selector: 'app-instance-template-group-detail',
  templateUrl: './instance-template-group-detail.component.html',
  styleUrls: ['./instance-template-group-detail.component.css']
})
export class InstanceTemplateGroupDetailComponent implements OnInit {

  @Input()
  group: InstanceTemplateGroup;

  @Input()
  config: ProcessConfigDto;

  @Input()
  product: ProductDto;

  @Input()
  status: ApplyMessage[][];

  appNames: string[] = [];
  appDescriptions: string[] = [];

  private overlayRef: OverlayRef;

  constructor(
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    private iterableDiffers: IterableDiffers
  ) { }

  ngOnInit(): void {
    for (let i = 0; i < this.group.applications.length; ++i) {
      const app = this.group.applications[i];
      this.appNames[i] = app.name ? app.name : this.calculateName(app, this.group.type);
      this.appDescriptions[i] = app.description ? app.description : ('A default ' + this.appNames[i]);
    }
  }

  hasErrors(messages: ApplyMessage[]) {
    return this.hasIcon(messages, 'error');
  }

  hasWarnings(messages: ApplyMessage[]) {
    return this.hasIcon(messages, 'warning');
  }

  hasIcon(messages: ApplyMessage[], icon: string) {
    if (!messages) {
      return false;
    }
    for (const msg of messages) {
      if (msg.icon === icon) {
        return true;
      }
    }
    return false;
  }

  getTopIcon() {
    if (this.status) {
      for (const perApp of this.status) {
        if (this.hasErrors(perApp)) {
          return 'error';
        }
      }
      for (const perApp of this.status) {
        if (this.hasWarnings(perApp)) {
          return 'warning';
        }
      }
    }
    return 'info';
  }

  getClass(icon: string) {
    if (icon === 'error') {
      return 'template-error';
    } else if (icon === 'warning') {
      return 'template-warning';
    } else {
      return 'template-message';
    }
  }

  getChipColor(app: InstanceTemplateApplication, status: ApplyMessage[]) {
    if (this.hasErrors(status)) {
      return 'warn';
    } else if (this.hasWarnings(status)) {
      return 'accent';
    } else {
      return undefined;
    }
  }

  calculateName(app: InstanceTemplateApplication, type: ApplicationType): string {
    const appGrp = this.getApplicationGroup(type, app);
    return appGrp?.appName ? appGrp.appName : 'Unknown';
  }

  private getApplicationGroup(type: ApplicationType, app: InstanceTemplateApplication) {
    return (type === ApplicationType.CLIENT ? this.config.clientApps : this.config.serverApps).find(a => a.appKeyName === this.product.product + '/' + app.application);
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
      disposeOnNavigation: true,
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
