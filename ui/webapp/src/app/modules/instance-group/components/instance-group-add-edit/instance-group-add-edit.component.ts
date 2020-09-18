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
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { CustomPropertyEditComponent } from 'src/app/modules/shared/components/custom-property-edit/custom-property-edit.component';
import { CustomPropertyValueComponent } from 'src/app/modules/shared/components/custom-property-value/custom-property-value.component';
import { EMPTY_INSTANCE_GROUP, EMPTY_PROPERTIES_RECORD } from '../../../../models/consts';
import { CustomPropertiesRecord, CustomPropertyDescriptor, InstanceGroupConfiguration, MinionMode } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { ErrorMessage, Logger, LoggingService } from '../../../core/services/logging.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../../../shared/services/messagebox.service';
import { InstanceGroupValidators } from '../../../shared/validators/instance-group.validator';
import { InstanceGroupService } from '../../services/instance-group.service';

@Component({
  selector: 'app-instance-group-add-edit',
  templateUrl: './instance-group-add-edit.component.html',
  styleUrls: ['./instance-group-add-edit.component.css'],
  providers: [SettingsService]
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

  public instancePropertiesDescriptors: CustomPropertyDescriptor[] = []; // shortcut into instance group record

  public instanceGroupProperties: CustomPropertiesRecord;

  public clonedInstanceGroup: InstanceGroupConfiguration;
  public clonedInstanceGroupProperties: CustomPropertiesRecord;

  private overlayRef: OverlayRef;

  public instanceGroupFormGroup = this.fb.group({
    name: ['', [Validators.required, InstanceGroupValidators.namePattern]],
    title: [''],
    description: ['', Validators.required],
    logo: [''],
    autoDelete: [''],
    managed: [''],
    instanceProperties: ['']
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
    private dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.nameParam = this.route.snapshot.paramMap.get('name');
    this.log.debug('nameParam = ' + this.nameParam);

    if (this.isCreate()) {
      // prepare instance group
      const instanceGroup = cloneDeep(EMPTY_INSTANCE_GROUP);
      this.clonedInstanceGroup = cloneDeep(instanceGroup);
      this.instanceGroupFormGroup.setValue(instanceGroup);
      this.instancePropertiesDescriptors = instanceGroup.instanceProperties;
      // prepare instance group properties values
      this.instanceGroupProperties = cloneDeep(EMPTY_PROPERTIES_RECORD);
      this.clonedInstanceGroupProperties = cloneDeep(EMPTY_PROPERTIES_RECORD);
    } else {
      const og = this.instanceGroupService.getInstanceGroup(this.nameParam);
      const op = this.instanceGroupService.getInstanceGroupProperties(this.nameParam);
      forkJoin(og, op).subscribe(
        result => {
          const instanceGroup: InstanceGroupConfiguration = result[0];

          if (!instanceGroup.instanceProperties) {
            instanceGroup.instanceProperties = [];
          }
          this.instanceGroupFormGroup.setValue(instanceGroup);
          this.clonedInstanceGroup = cloneDeep(instanceGroup);
          this.instancePropertiesDescriptors = instanceGroup.instanceProperties;

          if (instanceGroup.logo) {
            this.instanceGroupService.getInstanceGroupImage(this.nameParam)
              .subscribe((data) => {
                const reader = new FileReader();
                reader.onload = () => {
                  this.originalLogoUrl = this.sanitizer.bypassSecurityTrustUrl(
                    reader.result.toString()
                  );
                };
                reader.readAsDataURL(data);
              });
          }

          this.instanceGroupProperties = result[1];
          if (!this.instanceGroupProperties.properties) {
            this.instanceGroupProperties.properties = {};
          }
          this.clonedInstanceGroupProperties = cloneDeep(this.instanceGroupProperties);
        },
        error => {
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
            message:
              'Please choose a different image. Supported types: jpeg, png, gif or svg',
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
    const instanceGroupProperties = this.instanceGroupProperties;

    if (this.isCreate()) {
      // first create group, second set properties third update image on existing group
      this.instanceGroupService.createInstanceGroup(instanceGroup)
        .subscribe((result) => {
          this.log.info('created new instance group ' + instanceGroup.name);
          this.clonedInstanceGroup = instanceGroup;
          this.instanceGroupService
            .updateInstanceGroupProperties(instanceGroup.name, instanceGroupProperties)
            .pipe(finalize(() => { this.loading = false; }))
            .subscribe(result => {
                this.clonedInstanceGroupProperties = cloneDeep(instanceGroupProperties);
                this.checkImage(instanceGroup.name);
              });
        });
    } else {
      if (this.config.config.mode === MinionMode.CENTRAL) {
        this.messageBoxService.open({
            title: 'Updating Managed Servers',
            message: 'This action will try to contact and synchronize with all managed servers for this instance group.',
            mode: MessageBoxMode.CONFIRM_WARNING,
          })
          .subscribe((r) => {
            if (r) {
              this.doUpdate(instanceGroup, instanceGroupProperties);
            } else {
              this.loading = false;
            }
          });
      } else {
        this.doUpdate(instanceGroup, instanceGroupProperties);
      }
    }
  }

  private doUpdate(instanceGroup: any, instanceGroupProperties: CustomPropertiesRecord) {
    const og = this.instanceGroupService.updateInstanceGroup(this.nameParam, instanceGroup);
    const op = this.instanceGroupService.updateInstanceGroupProperties(this.nameParam, this.instanceGroupProperties);
    forkJoin(og, op)
    .pipe(
      finalize(() => {this.loading = false;})
    )
    .subscribe((result) => {
      this.log.info('updated instance group ' + this.nameParam);
      this.clonedInstanceGroup = instanceGroup;
      this.clonedInstanceGroupProperties = instanceGroupProperties;
      this.checkImage(this.nameParam);
    });
  }

  isModified(): boolean {
    return this.isConfigurationModified() || this.isPropertiesModified();
  }

  isConfigurationModified(): boolean {
    const instanceGroup: InstanceGroupConfiguration = this.instanceGroupFormGroup.getRawValue();
    return (
      !isEqual(instanceGroup, this.clonedInstanceGroup) ||
      this.newLogoFile != null
    );
  }

  isPropertiesModified(): boolean {
    return !isEqual(this.instanceGroupProperties, this.clonedInstanceGroupProperties);
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
      this.instanceGroupService.updateInstanceGroupImage(group, this.newLogoFile)
        .pipe(finalize(() => {this.clearLogoUpload(); this.loading = false; }))
        .subscribe((response) => {
          this.router.navigate(['/instancegroup/browser']);
        });
    } else {
      this.loading = false;
      this.router.navigate(['/instancegroup/browser']);
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

  addInstanceProperty() {
    this.dialog.open(CustomPropertyEditComponent, {
      width: '500px',
      data: null,
    }).afterClosed().subscribe(r => {
      if (r) {
        this.instancePropertiesDescriptors.push(r);
        this.sortInstanceProperties();
      }
    });
  }

  editInstanceProperty(property: CustomPropertyDescriptor, index: number) {
    this.dialog.open(CustomPropertyEditComponent, {
      width: '500px',
      data: cloneDeep(property),
    }).afterClosed().subscribe(r => {
      if (r) {
        this.instancePropertiesDescriptors.splice(index, 1, r);
        this.sortInstanceProperties();
      }
    });
  }

  removeInstanceProperty(index: number) {
    this.instancePropertiesDescriptors.splice(index, 1);
  }

  private sortInstanceProperties() {
      this.instancePropertiesDescriptors = this.instancePropertiesDescriptors.sort((a,b) => a.name.localeCompare(b.name));
  }

  // InstanceGroup Property Values

  addInstanceGroupProperty() {
    const possibleDescriptors = this.settings.getSettings().instanceGroup.properties.filter(d => !this.instanceGroupProperties.properties[d.name] );
    this.dialog.open(CustomPropertyValueComponent, {
      width: '500px',
      data: {
        descriptors: possibleDescriptors,
        propertyName: null,
        propertyValue: null,
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        this.instanceGroupProperties.properties[r.name] = r.value;
      }
    });
  }

  editInstanceGroupProperty(propertyName: string) {
    let descriptor = this.settings.getSettings().instanceGroup.properties.find(d => d.name === propertyName);
    if (!descriptor) {
      descriptor = {name: propertyName, description: ''};
    }
    this.dialog.open(CustomPropertyValueComponent, {
      width: '500px',
      data: {
        descriptors: [descriptor],
        propertyName: propertyName,
        propertyValue: cloneDeep(this.instanceGroupProperties.properties[propertyName])
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        this.instanceGroupProperties.properties[r.name] = r.value;
      }
    });
  }

  removeInstanceGroupProperty(propertyName: string) {
    delete this.instanceGroupProperties.properties[propertyName];
  }

  hasInstanceGroupProperties(): boolean {
    return Object.keys(this.instanceGroupProperties.properties).length > 0;
  }

  getSortedInstanceGroupPropertyKeys(): string[] {
    if (this.instanceGroupProperties?.properties) {
      return Object.keys(this.instanceGroupProperties.properties).sort((a, b) => a.localeCompare(b));
    } else {
      return [];
    }
  }

}
