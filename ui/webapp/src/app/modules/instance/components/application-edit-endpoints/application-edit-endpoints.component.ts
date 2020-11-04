import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { cloneDeep, isEqual } from 'lodash-es';
import { Observable, of } from 'rxjs';
import { ApplicationConfiguration, ApplicationDescriptor, HttpAuthenticationType } from 'src/app/models/gen.dtos';
import { EditAppConfigContext, ProcessConfigDto } from 'src/app/models/process.model';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';

@Component({
  selector: 'app-application-edit-endpoints',
  templateUrl: './application-edit-endpoints.component.html',
  styleUrls: ['./application-edit-endpoints.component.css'],
})
export class ApplicationEditEndpointsComponent implements OnInit {
  @Input() instanceGroup: string;
  @Input() processConfig: ProcessConfigDto;
  @Input() appConfigContext: EditAppConfigContext;
  @Input() appDesc: ApplicationDescriptor;
  @Input() readonly = false;

  @Output() validationStateChanged = new EventEmitter<boolean>();

  /** Original state of the configuration */
  public clonedAppConfig: ApplicationConfiguration;

  /** The form holding the current values */
  public formGroup = new FormGroup({});

  constructor(private messageBoxService: MessageboxService) {}

  ngOnInit() {
    this.clonedAppConfig = cloneDeep(this.appConfigContext.applicationConfiguration);

    this.updateFormGroup();

    // Disable all controls in readonly mode
    if (this.readonly) {
      this.formGroup.disable();
    }

    // Notify if the form changes
    this.formGroup.statusChanges.subscribe((status) => {
      const isValid = status === 'VALID';
      this.validationStateChanged.emit(isValid);
    });

    // this is required to run asynchronously to avoid changes to the model while updating the view.
    setTimeout(() => this.validationStateChanged.emit(false), 0);
  }

  /** Adds the static fields to configure the application */
  updateFormGroup() {
    for (const ep of this.appConfigContext.applicationConfiguration.endpoints.http) {
      // create all required controls.
      const fcPath = new FormControl();
      fcPath.disable();
      fcPath.setValue(ep.path);

      const fcPort = new FormControl();
      fcPort.setValue(ep.port);
      fcPort.valueChanges.subscribe((v) => (ep.port = v));
      fcPort.setValidators([Validators.required]);

      const fcAuthType = new FormControl();
      fcAuthType.setValue(ep.authType ? ep.authType : HttpAuthenticationType.NONE);
      fcAuthType.valueChanges.subscribe((v) => (ep.authType = v));

      const fcUser = new FormControl();
      fcUser.setValue(ep.authUser);
      fcUser.valueChanges.subscribe((v) => (ep.authUser = v));
      fcUser.setValidators([Validators.required]);

      const fcPass = new FormControl();
      fcPass.setValue(ep.authPass);
      fcPass.valueChanges.subscribe((v) => (ep.authPass = v));
      fcPass.setValidators([Validators.required]);

      const updateAuthType: (value: any) => void = (v) => {
        if (ep.authType === HttpAuthenticationType.BASIC || ep.authType === HttpAuthenticationType.DIGEST) {
          [fcUser, fcPass].forEach((c) => {
            c.enable();
          });
        } else {
          [fcUser, fcPass].forEach((c) => {
            c.disable();
          });
        }
      };

      updateAuthType(ep.authType);
      fcAuthType.valueChanges.subscribe(updateAuthType);

      const fcSecure = new FormControl();
      fcSecure.setValue(ep.secure);
      fcSecure.valueChanges.subscribe((v) => (ep.secure = v));

      const fcTrustAll = new FormControl();
      fcTrustAll.setValue(ep.trustAll);
      fcTrustAll.valueChanges.subscribe((v) => (ep.trustAll = v));

      const fcKeyStorePass = new FormControl();
      fcKeyStorePass.setValue(ep.trustStorePass);
      fcKeyStorePass.valueChanges.subscribe((v) => (ep.trustStorePass = v));

      const fcKeyStore = new FormControl();
      fcKeyStore.setValue(ep.trustStore);
      fcKeyStore.valueChanges.subscribe((v) => (ep.trustStore = v));

      const updateSecurityState: (value: boolean) => void = (v) => {
        if (v) {
          fcTrustAll.enable();
          if (!fcTrustAll.value) {
            fcKeyStore.enable();
            fcKeyStorePass.enable();
          }
        } else {
          fcTrustAll.disable();
          fcKeyStore.disable();
          fcKeyStorePass.disable();
        }
      };

      updateSecurityState(ep.secure);
      fcSecure.valueChanges.subscribe((v) => updateSecurityState(v));

      const updateTrustState: (value: boolean) => void = (v) => {
        if (v) {
          fcKeyStore.disable();
          fcKeyStorePass.disable();
        } else {
          fcKeyStore.enable();
          fcKeyStorePass.enable();
        }
      };

      updateTrustState(ep.trustAll);
      fcTrustAll.valueChanges.subscribe((v) => updateTrustState(v));

      this.formGroup.addControl(ep.id + '_path', fcPath);
      this.formGroup.addControl(ep.id + '_port', fcPort);
      this.formGroup.addControl(ep.id + '_authType', fcAuthType);
      this.formGroup.addControl(ep.id + '_user', fcUser);
      this.formGroup.addControl(ep.id + '_pass', fcPass);
      this.formGroup.addControl(ep.id + '_secure', fcSecure);
      this.formGroup.addControl(ep.id + '_trustAll', fcTrustAll);
      this.formGroup.addControl(ep.id + '_trustStore', fcKeyStore);
      this.formGroup.addControl(ep.id + '_trustStorePass', fcKeyStorePass);
    }
  }

  isCtrlValid(paramUid: string) {
    return this.formGroup.controls[paramUid].valid;
  }

  getCtrlErrorMessage(paramUid: string) {
    const control = this.formGroup.controls[paramUid];
    // Numeric must be the first check as a non-numeric value
    // is provided as empty string by the browser
    if (control.hasError('numeric')) {
      return 'Only numeric values are allowed.';
    }
    if (control.hasError('required')) {
      return 'Mandatory input required.';
    }
    return 'Unknown error';
  }

  /**
   * Asks the user whether or not to discard changes if dirty.
   */
  public canDeactivate(): Observable<boolean> {
    if (!this.isDirty()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message:
        'Application "' + this.appConfigContext.applicationConfiguration.name + '" was modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

  /**
   * Returns whether or not the current application config is valid.
   */
  public isValid() {
    if (this.readonly) {
      return true;
    }
    return this.formGroup.valid;
  }

  /**
   * Returns whether or not there are local changes in this component
   */
  public isDirty() {
    if (this.readonly) {
      return false;
    }
    return !isEqual(this.clonedAppConfig, this.appConfigContext.applicationConfiguration);
  }
}
