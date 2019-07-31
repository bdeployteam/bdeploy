import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { cloneDeep, isEqual } from 'lodash';
import { Observable, of } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { MessageBoxMode } from '../messagebox/messagebox.component';
import { EMPTY_INSTANCE_GROUP } from '../models/consts';
import { InstanceGroupConfiguration } from '../models/gen.dtos';
import { InstanceGroupService } from '../services/instance-group.service';
import { ErrorMessage, Logger, LoggingService } from '../services/logging.service';
import { MessageboxService } from '../services/messagebox.service';
import { InstanceGroupValidators } from '../validators/instance-group.validator';

@Component({
  selector: 'app-instance-group-add-edit',
  templateUrl: './instance-group-add-edit.component.html',
  styleUrls: ['./instance-group-add-edit.component.css'],
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

  public clonedInstanceGroup: InstanceGroupConfiguration;

  public instanceGroupFormGroup = this.fb.group({
    name: ['', [Validators.required, InstanceGroupValidators.namePattern]],
    description: ['', Validators.required],
    logo: [''],
  });

  get nameControl() {
    return this.instanceGroupFormGroup.get('name');
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
  ) {}

  ngOnInit() {
    this.nameParam = this.route.snapshot.paramMap.get('name');
    this.log.debug('nameParam = ' + this.nameParam);

    if (this.isCreate()) {
      const instanceGroup = cloneDeep(EMPTY_INSTANCE_GROUP);
      this.instanceGroupFormGroup.setValue(instanceGroup);
      this.clonedInstanceGroup = cloneDeep(instanceGroup);
    } else {
      this.instanceGroupService.getInstanceGroup(this.nameParam).subscribe(
        instanceGroup => {
          this.log.debug('got instance group ' + this.nameParam);
          this.instanceGroupFormGroup.setValue(instanceGroup);
          this.clonedInstanceGroup = cloneDeep(instanceGroup);

          if (instanceGroup.logo) {
            this.instanceGroupService.getInstanceGroupImage(this.nameParam).subscribe(data => {
              this.log.debug('got logo data...');
              const reader = new FileReader();
              reader.onload = () => {
                this.originalLogoUrl = this.sanitizer.bypassSecurityTrustUrl(reader.result.toString());
              };
              reader.readAsDataURL(data);
            });
          } else {
            this.log.debug('no logo to read...');
          }
        },
        error => {
          this.log.error(new ErrorMessage('reading instance group failed', error));
        },
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

    if (this.isCreate()) {
      this.instanceGroupService
        .createInstanceGroup(instanceGroup)
        .pipe(
          finalize(() => {
            this.clearLogoUpload();
            this.loading = false;
          }),
        )
        .subscribe(result => {
          this.log.info('created new instance group ' + instanceGroup.name);
          this.checkImage(instanceGroup.name);
          this.clonedInstanceGroup = instanceGroup;
        });
    } else {
      this.instanceGroupService
        .updateInstanceGroup(this.nameParam, instanceGroup)
        .pipe(
          finalize(() => {
            this.clearLogoUpload();
            this.loading = false;
          }),
        )
        .subscribe(result => {
          this.log.info('updated instance group ' + this.nameParam);
          this.checkImage(this.nameParam);
          this.clonedInstanceGroup = instanceGroup;
        });
    }
  }

  isModified(): boolean {
    const instanceGroup: InstanceGroupConfiguration = this.instanceGroupFormGroup.getRawValue();
    return !isEqual(instanceGroup, this.clonedInstanceGroup) || this.newLogoFile != null;
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
      this.instanceGroupService.updateInstanceGroupImage(group, this.newLogoFile).pipe(finalize(() => this.loading = false)).subscribe(
        response => {
          this.router.navigate(['/instancegroup/browser']);
          this.loading = false;
        }
      );
    } else {
      this.router.navigate(['/instancegroup/browser']);
      this.loading = false;
    }
  }
}
