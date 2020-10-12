import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, Input, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatTabChangeEvent } from '@angular/material/tabs';
import { finalize } from 'rxjs/operators';
import { PluginAdminService } from 'src/app/modules/admin/services/plugin-admin.service';
import { ProductService } from 'src/app/modules/shared/services/product.service';
import { InstanceTemplateDescriptor, InstanceUsageDto, PluginInfoDto, ProductDto } from '../../../../models/gen.dtos';

@Component({
  selector: 'app-product-info-card',
  templateUrl: './product-info-card.component.html',
  styleUrls: ['./product-info-card.component.css']
})
export class ProductInfoCardComponent implements OnInit {

  @Input() public instanceGroup: string;
  @Input() public productDto: ProductDto;
  @Input() public showUsedIn: boolean = true;

  usedIn: InstanceUsageDto[];
  loadingUsedIn = false;

  plugins: PluginInfoDto[];
  loadingPlugins = false;

  private overlayRef: OverlayRef;

  constructor(
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    private productService: ProductService,
    private pluginAdmin: PluginAdminService,
  ) { }

  ngOnInit() {
  }

  public getLabelKeys(): string[] {
    return this.productDto ? Object.keys(this.productDto.labels) : [];
  }

  getApplicationCount(template: InstanceTemplateDescriptor) {
    return template.groups.map(g => g.applications.length).reduce((p, c) => p + c);
  }

  onTabChange(event: MatTabChangeEvent) {
    if (event.tab.textLabel === 'Used In') {
      if (!this.usedIn && !this.loadingUsedIn) {
        this.loadingUsedIn = true;
        this.productService.getProductVersionUsedIn(this.instanceGroup, this.productDto.key).pipe(finalize(() => this.loadingUsedIn = false)).subscribe(r => {
          this.usedIn = r;
        });
      }
    } else if(event.tab.textLabel === 'Plugins') {
      if (!this.plugins && !this.loadingPlugins) {
        this.loadingPlugins = true;
        this.pluginAdmin.getProductPlugins(this.instanceGroup, this.productDto.key).subscribe(r => {
          this.plugins = r;
        })
      }
    }
  }

  openOverlay(relative: MatButton, template: TemplateRef<any>) {

    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      width: '650px',
      height: '400px',
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
