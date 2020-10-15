import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { cloneDeep, isEqual } from 'lodash-es';
import { forkJoin, Observable, of } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { CustomAttributeValueComponent } from 'src/app/modules/shared/components/custom-attribute-value/custom-attribute-value.component';
import { EMPTY_ATTRIBUTES_RECORD, EMPTY_INSTANCE } from '../../../../models/consts';
import { CustomAttributesRecord, InstanceConfiguration, InstanceGroupConfiguration, InstancePurpose, InstanceVersionDto, ManagedMasterDto, MinionMode, ProductDto } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { InstanceGroupService } from '../../../instance-group/services/instance-group.service';
import { ManagedServersService } from '../../../servers/services/managed-servers.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../../../shared/services/messagebox.service';
import { ProductService } from '../../../shared/services/product.service';
import { InstanceService } from '../../services/instance.service';

@Component({
  selector: 'app-instance-add-edit',
  templateUrl: './instance-add-edit.component.html',
  styleUrls: ['./instance-add-edit.component.css'],
})
export class InstanceAddEditComponent implements OnInit {
  private log: Logger = this.loggingService.getLogger('InstanceAddEditComponent');

  public groupParam: string;
  public uuidParam: string;

  public purposes: InstancePurpose[];
  public products: ProductDto[] = [];
  public servers: ManagedMasterDto[] = [];
  public tags: string[] = [];

  public loading = false;
  public loadingText: string;

  public instanceAttributes: CustomAttributesRecord;
  public clonedInstanceAttributes: CustomAttributesRecord;

  public instanceGroup: InstanceGroupConfiguration;
  public clonedInstance: InstanceConfiguration;
  private expectedVersion: InstanceVersionDto;

  public instanceFormGroup = this.formBuilder.group({
    uuid: ['', Validators.required],
    name: ['', Validators.required],
    description: [''],
    autoStart: [''],
    configTree: [''],
    purpose: ['', Validators.required],
    product: this.formBuilder.group({
      name: ['', Validators.required],
      tag: ['', Validators.required],
    }),
    autoUninstall: [''],
  });

  public formGroup = this.formBuilder.group({
    managedServer: this.formBuilder.group({
      name: [''],
    }),
  });

  private overlayRef: OverlayRef;

  constructor(
    private formBuilder: FormBuilder,
    private instanceGroupService: InstanceGroupService,
    private productService: ProductService,
    private instanceService: InstanceService,
    private route: ActivatedRoute,
    private loggingService: LoggingService,
    private messageBoxService: MessageboxService,
    public location: Location,
    private viewContainerRef: ViewContainerRef,
    private overlay: Overlay,
    private config: ConfigService,
    private managedServersService: ManagedServersService,
    public routingHistoryService: RoutingHistoryService,
    private dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.groupParam = this.route.snapshot.paramMap.get('group');
    this.uuidParam = this.route.snapshot.paramMap.get('uuid');

    this.loading = true;
    this.instanceFormGroup.disable();
    this.formGroup.disable();

    if (this.isCreate()) {
      this.loadingText = 'Generating UUID...';
      forkJoin({
        instanceGroup: this.instanceGroupService.getInstanceGroup(this.groupParam),
        uuid: this.instanceGroupService.createUuid(this.groupParam),
        purposes: this.instanceService.listPurpose(this.groupParam),
        products: this.productService.getProducts(this.groupParam, null),
        servers: this.isCentral() ? this.managedServersService.getManagedServers(this.groupParam) : of([]),
      }).pipe(finalize(() => {this.loading = false; }))
      .subscribe(r => {
        const instance = cloneDeep(EMPTY_INSTANCE);
        instance.autoUninstall = true;
        instance.uuid = r.uuid;
        this.instanceFormGroup.patchValue(instance);
        this.clonedInstance = cloneDeep(instance);
        this.instanceGroup = r.instanceGroup;
        this.purposes = r.purposes;
        this.products = r.products;
        this.instanceAttributes = cloneDeep(EMPTY_ATTRIBUTES_RECORD);
        this.clonedInstanceAttributes = cloneDeep(EMPTY_ATTRIBUTES_RECORD);
        this.servers = r.servers.sort((a, b) => a.hostName.localeCompare(b.hostName));

        this.instanceFormGroup.enable();
        this.formGroup.enable();
        this.productTagControl.disable();
      });
    } else {
      this.loadingText = 'Loading...';
      forkJoin({
        instanceGroup: this.instanceGroupService.getInstanceGroup(this.groupParam),
        instance: this.instanceService.getInstance(this.groupParam, this.uuidParam),
        instanceVersions: this.instanceService.listInstanceVersions(this.groupParam, this.uuidParam),
        managedServer: this.managedServersService.getServerForInstance(this.groupParam, this.uuidParam, null),
        purposes: this.instanceService.listPurpose(this.groupParam),
        products: this.productService.getProducts(this.groupParam, null),
        attributes: this.instanceService.getInstanceAttributes(this.groupParam, this.uuidParam),
        servers: this.isCentral() ? this.managedServersService.getManagedServers(this.groupParam) : of([]),
      }).pipe(finalize(() => {this.loading = false; }))
        .subscribe(r => {
          this.instanceGroup = r.instanceGroup;
          this.instanceFormGroup.patchValue(r.instance);
          this.clonedInstance = cloneDeep(r.instance);
          r.instanceVersions.sort((a, b) => {
            return +b.key.tag - +a.key.tag;
          });
          this.expectedVersion = r.instanceVersions[0];
          if (r.managedServer) {
            this.managedServerControl.setValue(r.managedServer.hostName);
          }
          this.purposes = r.purposes;
          this.products = r.products;
          this.instanceAttributes = r.attributes;
          if (!this.instanceAttributes.attributes) {
            this.instanceAttributes.attributes = {};
          }
          this.clonedInstanceAttributes = cloneDeep(r.attributes);
          this.servers = r.servers.sort((a, b) => a.hostName.localeCompare(b.hostName));
          this.onSelectProduct(this.clonedInstance.product.name); // calculate tags for product

          this.instanceFormGroup.enable();
          this.productNameControl.disable();
          this.productTagControl.disable();
        });
    }

    if (this.isCentral()) {
      this.managedServerControl.setValidators([Validators.required]);
    }
  }

  isModified(): boolean {
    return this.isConfigurationModified() || this.isAttributesModified();
  }

  isConfigurationModified(): boolean {
    const instance: InstanceConfiguration = this.instanceFormGroup.getRawValue();
    return !isEqual(instance, this.clonedInstance);
  }

  isAttributesModified(): boolean {
    return !isEqual(this.instanceAttributes, this.clonedInstanceAttributes);
  }

  canDeactivate(): Observable<boolean> {
    if (!this.isModified()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Instance was modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

  public getErrorMessage(ctrl: FormControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    }
    return 'Unknown error';
  }

  public isCreate(): boolean {
    return this.uuidParam == null;
  }

  public getProductNames(): string[] {
    const pNames = Array.from(this.products, (p) => p.key.name);
    return pNames.filter((value, index, array) => array.indexOf(value) === index).sort();
  }

  public getProductDisplayName(pName: string): string {
    const product: ProductDto = this.products.find((e) => e.key.name === pName);
    return product.name + (product.vendor ? ' (' + product.vendor + ')' : '');
  }

  onSelectProduct(selectedProduct: string) {
    this.tags = [];
    this.productTagControl.disable();
    if (selectedProduct) {
      const productVersions = this.products.filter((value, index, array) => value.key.name === selectedProduct);
      this.tags = Array.from(productVersions, (p) => p.key.tag);
      if (this.tags.length > 0) {
        this.productTagControl.enable();
      }
    }
  }

  public onSubmit(): void {
    this.loading = true;
    this.loadingText = 'Saving...';
    const instance: InstanceConfiguration = this.instanceFormGroup.getRawValue();
    const managedServer = this.managedServerControl ? this.managedServerControl.value : null;
    if (this.isCreate()) {
      // first create instance, second set attributes on existing instance
      this.instanceService.createInstance(this.groupParam, instance, managedServer)
      .subscribe(r => {
        this.clonedInstance = instance;
        this.instanceService.updateInstanceAttributes(this.groupParam, instance.uuid, this.instanceAttributes)
        .pipe(finalize(() => this.loading = false))
        .subscribe(a => {
          this.clonedInstanceAttributes = this.instanceAttributes;
          this.location.back();
        });
      });
    } else {
      forkJoin({
        configuration: this.isConfigurationModified() ? this.instanceService.updateInstance(this.groupParam, this.uuidParam, instance, null, managedServer, this.expectedVersion.key.tag) : of(null),
        attributes: this.isAttributesModified() ? this.instanceService.updateInstanceAttributes(this.groupParam, this.uuidParam, this.instanceAttributes) : of(null),
      }).pipe(finalize(() => this.loading = false))
        .subscribe(_ => {
          this.clonedInstance = instance;
          this.clonedInstanceAttributes = this.instanceAttributes;
          this.location.back();
        });
    }
  }

  get uuidControl() {
    return this.instanceFormGroup.get('uuid');
  }
  get nameControl() {
    return this.instanceFormGroup.get('name');
  }
  get descriptionControl() {
    return this.instanceFormGroup.get('description');
  }
  get purposeControl() {
    return this.instanceFormGroup.get('purpose');
  }

  get productNameControl() {
    return this.instanceFormGroup.get('product.name');
  }
  get productTagControl() {
    return this.instanceFormGroup.get('product.tag');
  }

  get managedServerControl() {
    return this.formGroup.get('managedServer.name');
  }

  openOverlay(relative: MatButton, template: TemplateRef<any>) {
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(relative._elementRef)
        .withPositions([
          {
            overlayX: 'end',
            overlayY: 'bottom',
            originX: 'center',
            originY: 'top',
          },
        ])
        .withPush()
        .withViewportMargin(0)
        .withDefaultOffsetX(35)
        .withDefaultOffsetY(-10),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: true,
      backdropClass: 'info-backdrop',
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

  isCentral() {
    return this.config.config.mode === MinionMode.CENTRAL;
  }

  addInstanceAttribute() {
    const possibleDescriptors = this.instanceGroup?.instanceAttributes?.filter(d => !this.instanceAttributes?.attributes[d.name] );
    this.dialog.open(CustomAttributeValueComponent, {
      width: '500px',
      data: {
        descriptors: possibleDescriptors,
        attributeName: null,
        attributeValue: null,
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        this.instanceAttributes.attributes[r.name] = r.value;
      }
    });
  }

  editInstanceAttribute(attributeName: string) {
    let descriptor = this.instanceGroup?.instanceAttributes?.find(d => d.name === attributeName);
    if (!descriptor) {
      descriptor = {name: attributeName, description: ''};
    }
    this.dialog.open(CustomAttributeValueComponent, {
      width: '500px',
      data: {
        descriptors: [descriptor],
        attributeName: attributeName,
        attributeValue: cloneDeep(this.instanceAttributes.attributes[attributeName])
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        this.instanceAttributes.attributes[r.name] = r.value;
      }
    });
  }

  removeInstanceAttribute(attributeName: string) {
    delete this.instanceAttributes.attributes[attributeName];
  }

  hasInstanceAttributes(): boolean {
    return this.instanceAttributes?.attributes && Object.keys(this.instanceAttributes.attributes).length > 0;
  }

  getSortedInstanceAttributesKeys(): string[] {
    if (this.instanceAttributes?.attributes) {
      return Object.keys(this.instanceAttributes.attributes).sort((a, b) => a.localeCompare(b));
    } else {
      return [];
    }
  }

}
