import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { cloneDeep, isEqual } from 'lodash-es';
import { forkJoin, Observable, of } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { RoutingHistoryService } from 'src/app/modules/legacy/core/services/routing-history.service';
import { CustomAttributeEditComponent } from 'src/app/modules/legacy/shared/components/custom-attribute-edit/custom-attribute-edit.component';
import { CustomAttributeValueComponent } from 'src/app/modules/legacy/shared/components/custom-attribute-value/custom-attribute-value.component';
import { EMPTY_ATTRIBUTES_RECORD, EMPTY_INSTANCE_GROUP } from '../../../../../models/consts';
import {
  CustomAttributeDescriptor,
  CustomAttributesRecord,
  InstanceGroupConfiguration,
  MinionMode,
} from '../../../../../models/gen.dtos';
import { MessageBoxMode } from '../../../../core/components/messagebox/messagebox.component';
import { ConfigService } from '../../../../core/services/config.service';
import { ErrorMessage, Logger, LoggingService } from '../../../../core/services/logging.service';
import { MessageboxService } from '../../../../core/services/messagebox.service';
import { InstanceGroupValidators } from '../../../../legacy/shared/validators/instance-group.validator';
import { InstanceGroupService } from '../../services/instance-group.service';

@Component({
  selector: 'app-instance-group-add-edit',
  templateUrl: './instance-group-add-edit.component.html',
  styleUrls: ['./instance-group-add-edit.component.css'],
  providers: [SettingsService],
})
export class InstanceGroupAddEditComponent implements OnInit {
  log: Logger = this.loggingService.getLogger('InstanceGroupAddEditComponent');

  nameParam: string;

  public loading = false;

  // logo data that was read on initialization for instanceGroup.logo
  public originalLogoUrl: SafeUrl = null;

  // filename and logo data for a new logo selected by the user
  public newLogoFile: File = null;
  public newLogoUrl: SafeUrl = null;

  public instanceAttributesDescriptors: CustomAttributeDescriptor[] = []; // shortcut into instance group record

  public instanceGroupAttributes: CustomAttributesRecord;

  public clonedInstanceGroup: InstanceGroupConfiguration;
  public clonedInstanceGroupAttributes: CustomAttributesRecord;

  private overlayRef: OverlayRef;

  public instanceGroupFormGroup = this.fb.group({
    name: ['', [Validators.required, InstanceGroupValidators.namePattern]],
    title: [''],
    description: ['', Validators.required],
    logo: [''],
    autoDelete: [''],
    managed: [''],
    instanceAttributes: [''],
    defaultInstanceGroupingAttribute: [''],
  });

  get nameControl() {
    return this.instanceGroupFormGroup.get('name');
  }
  get titleControl() {
    return this.instanceGroupFormGroup.get('title');
  }
  get descriptionControl() {
    return this.instanceGroupFormGroup.get('description');
  }
  get logoControl() {
    return this.instanceGroupFormGroup.get('logo');
  }
  get defaultInstanceGroupingAttributeControl() {
    return this.instanceGroupFormGroup.get('defaultInstanceGroupingAttribute');
  }

  constructor(
    private fb: FormBuilder,
    private instanceGroupService: InstanceGroupService,
    private router: Router,
    private route: ActivatedRoute,
    private sanitizer: DomSanitizer,
    private loggingService: LoggingService,
    private messageBoxService: MessageboxService,
    public location: Location,
    private viewContainerRef: ViewContainerRef,
    private overlay: Overlay,
    private config: ConfigService,
    private settings: SettingsService,
    public routingHistoryService: RoutingHistoryService,
    private dialog: MatDialog
  ) {}

  ngOnInit() {
    this.nameParam = this.route.snapshot.paramMap.get('name');
    this.log.debug('nameParam = ' + this.nameParam);

    if (this.isCreate()) {
      // prepare instance group
      const instanceGroup = cloneDeep(EMPTY_INSTANCE_GROUP);
      this.clonedInstanceGroup = cloneDeep(instanceGroup);
      this.instanceGroupFormGroup.setValue(instanceGroup);
      this.instanceAttributesDescriptors = instanceGroup.instanceAttributes;
      // prepare instance group attributes values
      this.instanceGroupAttributes = cloneDeep(EMPTY_ATTRIBUTES_RECORD);
      this.clonedInstanceGroupAttributes = cloneDeep(EMPTY_ATTRIBUTES_RECORD);
    } else {
      const og = this.instanceGroupService.getInstanceGroup(this.nameParam);
      const op = this.instanceGroupService.getInstanceGroupAttributes(this.nameParam);
      forkJoin([og, op]).subscribe(
        (result) => {
          const instanceGroup: InstanceGroupConfiguration = result[0];

          if (!instanceGroup.instanceAttributes) {
            instanceGroup.instanceAttributes = [];
          }
          this.instanceGroupFormGroup.setValue(instanceGroup);
          this.clonedInstanceGroup = cloneDeep(instanceGroup);
          this.instanceAttributesDescriptors = instanceGroup.instanceAttributes;

          if (instanceGroup.logo) {
            this.instanceGroupService.getInstanceGroupImage(this.nameParam).subscribe((data) => {
              const reader = new FileReader();
              reader.onload = () => {
                this.originalLogoUrl = this.sanitizer.bypassSecurityTrustUrl(reader.result.toString());
              };
              reader.readAsDataURL(data);
            });
          }

          this.instanceGroupAttributes = result[1];
          if (!this.instanceGroupAttributes.attributes) {
            this.instanceGroupAttributes.attributes = {};
          }
          this.clonedInstanceGroupAttributes = cloneDeep(this.instanceGroupAttributes);
        },
        (error) => {
          this.log.errorWithGuiMessage(new ErrorMessage('reading instance group failed', error));
        }
      );
    }
  }

  public getErrorMessage(ctrl: FormControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    } else if (ctrl.hasError('namePattern')) {
      return 'Must be a single word starting with a letter or digit, followed by valid characters: A-Z a-z 0-9 _ - .';
    }
    return 'Unknown error';
  }

  public isCreate(): boolean {
    return this.nameParam == null;
  }

  public onLogoChange(event) {
    const reader = new FileReader();
    if (event.target.files && event.target.files.length > 0) {
      const selLogoFile: File = event.target.files[0];
      reader.onload = () => {
        const selLogoUrl: string = reader.result.toString();
        if (
          selLogoUrl.startsWith('data:image/jpeg') ||
          selLogoUrl.startsWith('data:image/png') ||
          selLogoUrl.startsWith('data:image/gif') ||
          selLogoUrl.startsWith('data:image/svg+xml')
        ) {
          this.newLogoFile = selLogoFile;
          this.newLogoUrl = this.sanitizer.bypassSecurityTrustUrl(selLogoUrl);
          this.instanceGroupFormGroup.markAsDirty();
        } else {
          this.messageBoxService.open({
            title: 'Unsupported Image Type',
            message: 'Please choose a different image. Supported types: jpeg, png, gif or svg',
            mode: MessageBoxMode.ERROR,
          });
        }
      };
      reader.readAsDataURL(selLogoFile);
    }
  }

  public deleteLogo(): void {
    this.logoControl.setValue(null);
    this.instanceGroupFormGroup.markAsDirty();
    this.clearLogoUpload();
  }

  private clearLogoUpload(): void {
    this.newLogoUrl = null;
    this.newLogoFile = null;
  }

  public getLogoUrl(): SafeUrl {
    if (this.newLogoUrl == null) {
      if (this.logoControl.value == null) {
        return null;
      } else {
        return this.originalLogoUrl;
      }
    } else {
      return this.newLogoUrl;
    }
  }

  onSubmit(): void {
    this.loading = true;

    const instanceGroup = this.instanceGroupFormGroup.getRawValue();
    const instanceGroupAttributes = this.instanceGroupAttributes;

    if (this.isCreate()) {
      // first create group, second set attributes third update image on existing group
      this.instanceGroupService.createInstanceGroup(instanceGroup).subscribe((_) => {
        this.log.info('created new instance group ' + instanceGroup.name);
        this.clonedInstanceGroup = instanceGroup;
        this.instanceGroupService
          .updateInstanceGroupAttributes(instanceGroup.name, instanceGroupAttributes)
          .pipe(
            finalize(() => {
              this.loading = false;
            })
          )
          .subscribe((r) => {
            this.clonedInstanceGroupAttributes = cloneDeep(instanceGroupAttributes);
            this.checkImage(instanceGroup.name);
          });
      });
    } else {
      if (this.config.config.mode === MinionMode.CENTRAL) {
        this.messageBoxService
          .open({
            title: 'Updating Managed Servers',
            message:
              'This action will try to contact and synchronize with all managed servers for this instance group.',
            mode: MessageBoxMode.CONFIRM_WARNING,
          })
          .subscribe((r) => {
            if (r) {
              this.doUpdate(instanceGroup, instanceGroupAttributes);
            } else {
              this.loading = false;
            }
          });
      } else {
        this.doUpdate(instanceGroup, instanceGroupAttributes);
      }
    }
  }

  private doUpdate(instanceGroup: any, instanceGroupAttributes: CustomAttributesRecord) {
    forkJoin({
      configuration: this.isConfigurationModified()
        ? this.instanceGroupService.updateInstanceGroup(this.nameParam, instanceGroup)
        : of(null),
      attributes: this.isAttributesModified()
        ? this.instanceGroupService.updateInstanceGroupAttributes(this.nameParam, this.instanceGroupAttributes)
        : of(null),
    })
      .pipe(
        finalize(() => {
          this.loading = false;
        })
      )
      .subscribe((_) => {
        this.clonedInstanceGroup = instanceGroup;
        this.clonedInstanceGroupAttributes = instanceGroupAttributes;
        this.checkImage(this.nameParam);
      });
  }

  isModified(): boolean {
    return this.isConfigurationModified() || this.isAttributesModified();
  }

  isConfigurationModified(): boolean {
    const instanceGroup: InstanceGroupConfiguration = this.instanceGroupFormGroup.getRawValue();
    return !isEqual(instanceGroup, this.clonedInstanceGroup) || this.newLogoFile != null;
  }

  isAttributesModified(): boolean {
    return !isEqual(this.instanceGroupAttributes, this.clonedInstanceGroupAttributes);
  }

  canDeactivate(): Observable<boolean> {
    if (!this.isModified()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Instance group was modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

  private checkImage(group: string): void {
    if (this.newLogoFile != null) {
      this.instanceGroupService
        .updateInstanceGroupImage(group, this.newLogoFile)
        .pipe(
          finalize(() => {
            this.clearLogoUpload();
            this.loading = false;
          })
        )
        .subscribe((_) => {
          this.router.navigate(['/l/instancegroup/browser']);
        });
    } else {
      this.loading = false;
      this.router.navigate(['/l/instancegroup/browser']);
    }
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

  addInstanceAttribute() {
    this.dialog
      .open(CustomAttributeEditComponent, {
        width: '500px',
        data: null,
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          this.instanceAttributesDescriptors.push(r);
          this.sortInstanceAttributes();
        }
      });
  }

  editInstanceAttribute(attribute: CustomAttributeDescriptor, index: number) {
    this.dialog
      .open(CustomAttributeEditComponent, {
        width: '500px',
        data: cloneDeep(attribute),
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          this.instanceAttributesDescriptors.splice(index, 1, r);
          if (this.isDefaultAttribute(attribute) && attribute.name !== r.name) {
            this.toggleDefaultAttribute(r);
          }
          this.sortInstanceAttributes();
        }
      });
  }

  removeInstanceAttribute(attribute: CustomAttributeDescriptor, index: number) {
    if (this.isDefaultAttribute(attribute)) {
      this.defaultInstanceGroupingAttributeControl.setValue(undefined);
    }
    this.instanceAttributesDescriptors.splice(index, 1);
  }

  toggleDefaultAttribute(attribute: CustomAttributeDescriptor) {
    if (this.isDefaultAttribute(attribute)) {
      this.defaultInstanceGroupingAttributeControl.setValue(undefined);
    } else {
      this.defaultInstanceGroupingAttributeControl.setValue(attribute.name);
    }
  }

  isDefaultAttribute(attribute: CustomAttributeDescriptor): boolean {
    return attribute.name === this.defaultInstanceGroupingAttributeControl.value;
  }

  private sortInstanceAttributes() {
    this.instanceAttributesDescriptors = this.instanceAttributesDescriptors.sort((a, b) =>
      a.name.localeCompare(b.name)
    );
  }

  addInstanceGroupAttribute() {
    const possibleDescriptors = this.settings
      .getSettings()
      .instanceGroup.attributes.filter((d) => !this.instanceGroupAttributes.attributes[d.name]);
    this.dialog
      .open(CustomAttributeValueComponent, {
        width: '500px',
        data: {
          descriptors: possibleDescriptors,
          attributeName: null,
          attributeValue: null,
        },
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          this.instanceGroupAttributes.attributes[r.name] = r.value;
        }
      });
  }

  editInstanceGroupAttribute(attributeName: string) {
    let descriptor = this.settings.getSettings().instanceGroup.attributes.find((d) => d.name === attributeName);
    if (!descriptor) {
      descriptor = { name: attributeName, description: '' };
    }
    this.dialog
      .open(CustomAttributeValueComponent, {
        width: '500px',
        data: {
          descriptors: [descriptor],
          attributeName: attributeName,
          attributeValue: cloneDeep(this.instanceGroupAttributes.attributes[attributeName]),
        },
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          this.instanceGroupAttributes.attributes[r.name] = r.value;
        }
      });
  }

  removeInstanceGroupAttribute(attributeName: string) {
    delete this.instanceGroupAttributes.attributes[attributeName];
  }

  hasInstanceGroupAttributes(): boolean {
    return this.instanceGroupAttributes?.attributes && Object.keys(this.instanceGroupAttributes.attributes).length > 0;
  }

  getSortedInstanceGroupAttributeKeys(): string[] {
    if (this.instanceGroupAttributes?.attributes) {
      return Object.keys(this.instanceGroupAttributes.attributes).sort((a, b) => a.localeCompare(b));
    } else {
      return [];
    }
  }
}
