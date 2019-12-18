import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, TemplateRef, ViewContainerRef } from '@angular/core';
import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { MatButton } from '@angular/material/button';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { cloneDeep, isEqual } from 'lodash';
import { Observable, of } from 'rxjs';
import { CustomParameter, findFirstParameter, findLastParameter, GroupNames, LinkedParameter, NamedParameter, UnknownParameter } from '../../../../models/application.model';
import { CLIENT_NODE_NAME, EMPTY_PARAMETER_CONFIGURATION, EMPTY_PARAMETER_DESCRIPTOR } from '../../../../models/consts';
import { ApplicationConfiguration, ApplicationDescriptor, ApplicationStartType, ParameterDescriptor, ParameterType } from '../../../../models/gen.dtos';
import { EditAppConfigContext, ProcessConfigDto } from '../../../../models/process.model';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../../../shared/services/messagebox.service';
import { ParameterValidators } from '../../../shared/validators/parameter.validators';
import { ApplicationService } from '../../services/application.service';
import { ApplicationEditCommandPreviewComponent } from '../application-edit-command-preview/application-edit-command-preview.component';
import { ApplicationEditManualComponent, Context } from '../application-edit-manual/application-edit-manual.component';
import { ApplicationEditOptionalComponent } from '../application-edit-optional/application-edit-optional.component';

@Component({
  selector: 'app-application-edit',
  templateUrl: './application-edit.component.html',
  styleUrls: ['./application-edit.component.css'],
})
export class ApplicationEditComponent implements OnInit, OnDestroy {
  @Input()
  public instanceGroup: string;

  @Input()
  public processConfig: ProcessConfigDto;

  @Input()
  public appConfigContext: EditAppConfigContext;

  @Input()
  public appDesc: ApplicationDescriptor;

  @Input()
  public readonly: boolean;

  @Output()
  public validationStateChanged = new EventEmitter<boolean>();

  public parameterType = ParameterType;

  public expandedGroup: string;
  private overlayRef: OverlayRef;

  /** Original state of the configuration */
  public clonedAppConfig: ApplicationConfiguration;

  /** The form holding the current values */
  public formGroup = new FormGroup({});

  /** All parameters that are defined and that we know. */
  public linkedDescriptors = new Map<string, LinkedParameter>();

  /** All groups that we currently display. Sorted by their name */
  public sortedGroups: string[];

  /** All parameters that have been removed during a product upgrade / downgrade */
  public unknownParameters: UnknownParameter[];

  constructor(
    public appService: ApplicationService,
    private matDialog: MatDialog,
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    private messageBoxService: MessageboxService,
    private bottomSheet: MatBottomSheet,
  ) {}

  ngOnInit() {
    this.clonedAppConfig = cloneDeep(this.appConfigContext.applicationConfiguration);
    this.unknownParameters = this.appService.getUnknownParameters(this.appConfigContext.applicationConfiguration.uid);

    // Transform so that we can use them in the UI
    this.initParameters();
    this.initGroups();
    this.initFormGroup();
    this.updateFormGroup();
    this.updateAppCfgParameterOrder();

    // Disable all controls in readonly mode
    if (this.readonly) {
      this.formGroup.disable();
    }

    // Expand first group that has an error
    for (const group of this.sortedGroups) {
      if (this.getErrorTextForGroup(group)) {
        this.expandedGroup = group;
        break;
      }
    }

    // // Notify if the form changes
    this.formGroup.statusChanges.subscribe(status => {
      const isValid = status === 'VALID';
      this.validationStateChanged.emit(isValid);
    });

    // this is required to run asynchronously to avoid changes to the model while updating the view.
    setTimeout(() => this.validationStateChanged.emit(false), 0);
  }

  ngOnDestroy() {
    this.closeOverlay();
  }

  setExpandedGroup(groupName: string) {
    this.expandedGroup = groupName;
  }

  expandNextGroup() {
    const idx = this.sortedGroups.indexOf(this.expandedGroup);
    this.expandedGroup = this.sortedGroups[idx + 1];
    this.scrollExpandedGroupIntoView();
  }

  expandPreviousGroup() {
    const idx = this.sortedGroups.indexOf(this.expandedGroup);
    this.expandedGroup = this.sortedGroups[idx - 1];
    this.scrollExpandedGroupIntoView();
  }

  hasPreviousGroup(groupName: string) {
    return this.sortedGroups.indexOf(groupName) > 0;
  }

  hasNextGroup(groupName: string) {
    return this.sortedGroups.indexOf(groupName) < this.sortedGroups.length - 1;
  }

  hasMultipleGroups() {
    return this.sortedGroups.length > 1;
  }

  hasOptionalParameters(groupName: string) {
    return this.getOptionalParameters(groupName).length > 0;
  }

  hasCustomParameters(groupName: string) {
    return groupName === GroupNames.CUSTOM_PARAMETERS;
  }

  scrollExpandedGroupIntoView() {
    const idx = this.sortedGroups.indexOf(this.expandedGroup);
    const elementId = 'param-group-content-' + idx;
    document.getElementById(elementId).scrollIntoView();
  }

  public isClientApplication(): boolean {
    return this.appConfigContext.instanceNodeConfigurationDto.nodeName === CLIENT_NODE_NAME;
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

  getCtrlInputType(type: ParameterType) {
    switch (type) {
      case ParameterType.BOOLEAN:
        return 'checkbox';
      case ParameterType.NUMERIC:
        return 'number';
      case ParameterType.PASSWORD:
        return 'password';
    }
    return 'text';
  }

  getCtrlPlaceholderText(desc: ParameterDescriptor) {
    if (!desc.fixed) {
      return desc.name;
    }
    return desc.name + ' ' + '(Readonly)';
  }

  /** Returns a list of all parameters referring to the given group */
  getParametersOfGroup(groupName: string) {
    const params = Array.from(this.linkedDescriptors.values());
    const filtered = params.filter(lp => lp.rendered).filter(lp => lp.desc.groupName === groupName);

    // Globals first, then by parameter name
    return filtered.sort((a, b) => {
      if (a.desc.global && !b.desc.global) {
        return -1;
      }
      if (!a.desc.global && b.desc.global) {
        return 1;
      }
      return a.desc.name.localeCompare(b.desc.name);
    });
  }

  /** Returns a hint-text whether or not the app config contains error */
  getErrorTextForAppConfig() {
    let errorCount = 0;
    for (const ctrlName of Object.keys(this.formGroup.controls)) {
      const control = this.formGroup.controls[ctrlName];
      if (control.valid || control.disabled) {
        continue;
      }
      errorCount++;
    }
    if (errorCount === 0) {
      return null;
    }
    if (errorCount === 1) {
      return '1 validation issue detected';
    }
    return errorCount + ' validation issues detected';
  }

  /** Returns a hint-text how many parameters are configured in this group */
  getHintTextForGroup(groupName: string) {
    let configuredParams = 0;
    for (const linkedPara of Array.from(this.linkedDescriptors.values())) {
      if (linkedPara.desc.groupName !== groupName) {
        continue;
      }
      if (linkedPara.rendered) {
        configuredParams++;
      }
    }
    if (configuredParams === 0) {
      return 'No parameters have been configured';
    } else if (configuredParams === 1) {
      return '1 parameter has been configured';
    }
    return configuredParams + ' parameters have been configured';
  }

  /** Returns a hint-text whether or not the group contains errors. Null if the group is valid */
  getErrorTextForGroup(groupName: string) {
    const params = this.getParametersOfGroup(groupName);
    let errorCount = 0;
    for (const param of params) {
      const control = this.formGroup.controls[param.desc.uid];
      if (!control || control.valid || control.disabled) {
        continue;
      }
      errorCount++;
    }
    if (errorCount === 0) {
      return null;
    }
    if (errorCount === 1) {
      return '1 validation issue detected';
    }
    return errorCount + ' validation issues detected';
  }

  /**
   * Initializes all parameters that are defined by the product as well as the ones
   * that are declared in the application.
   */
  initParameters() {
    // Collect all parameters defined by the start command
    let previous: LinkedParameter = null;
    for (const paraDef of this.appDesc.startCommand.parameters) {
      const linked = new LinkedParameter(paraDef);
      if (!paraDef.groupName) {
        paraDef.groupName = GroupNames.UNGROUPED_PARAMETERS;
      }
      linked.conf = cloneDeep(EMPTY_PARAMETER_CONFIGURATION);
      linked.conf.uid = paraDef.uid;

      // Mandatory parameters must be visible
      linked.rendered = paraDef.mandatory;

      // Setup predecessor/successor chain
      if (previous) {
        linked.predecessor = linked;
        previous.successor = linked;
      }
      this.linkedDescriptors.set(paraDef.uid, linked);
      previous = linked;
    }

    // Collect all which are already configured
    previous = null;
    for (const paraCfg of this.appConfigContext.applicationConfiguration.start.parameters) {
      const linkedPara = this.linkedDescriptors.get(paraCfg.uid);

      // Make it visible and take its value
      if (linkedPara) {
        linkedPara.conf = cloneDeep(paraCfg);
        linkedPara.rendered = true;
        previous = linkedPara;
        continue;
      }
      // Skip parameters that have been removed during a product / upgrade downgrade
      const upIdx = this.unknownParameters.findIndex(up => up.descriptor.uid === paraCfg.uid);
      if (upIdx !== -1) {
        continue;
      }

      // Insert new parameter and setup its descriptor and config
      const lp = this.insertCustomParameter(paraCfg.uid, previous ? previous.desc.uid : null);

      lp.desc = cloneDeep(EMPTY_PARAMETER_DESCRIPTOR);
      lp.desc.mandatory = true;
      lp.desc.uid = paraCfg.uid;
      lp.desc.groupName = GroupNames.CUSTOM_PARAMETERS;
      lp.desc.name = paraCfg.uid;
      lp.desc.defaultValue = paraCfg.value;

      lp.conf = cloneDeep(paraCfg);
      previous = lp;
    }
  }

  /** Collects the available groups and sorts them by their name */
  initGroups() {
    // Collect all groups that are referenced by the parameters
    const groupSet = new Set<string>();
    const descriptors = Array.from(this.linkedDescriptors.values());
    for (const linkedDescriptor of descriptors) {
      groupSet.add(linkedDescriptor.desc.groupName);
    }
    // Ensure we have a custom entry
    groupSet.add(GroupNames.CUSTOM_PARAMETERS);

    this.sortedGroups = Array.from(groupSet);
    const cIdx = this.sortedGroups.indexOf(GroupNames.CUSTOM_PARAMETERS);
    if (cIdx !== -1) {
      this.sortedGroups.splice(cIdx, 1);
    }
    const uIdx = this.sortedGroups.indexOf(GroupNames.UNGROUPED_PARAMETERS);
    if (uIdx !== -1) {
      this.sortedGroups.splice(uIdx, 1);
    }

    // Sort remaining
    this.sortedGroups.sort();

    // append at the end
    if (uIdx !== -1) {
      this.sortedGroups.push(GroupNames.UNGROUPED_PARAMETERS);
    }
    if (cIdx !== -1) {
      this.sortedGroups.push(GroupNames.CUSTOM_PARAMETERS);
    }
  }

  /** Adds the static fields to configure the application */
  initFormGroup() {
    const appNameCtrl = new FormControl();
    appNameCtrl.setValidators([Validators.required]);
    appNameCtrl.setValue(this.appConfigContext.applicationConfiguration.name);
    appNameCtrl.valueChanges.subscribe(v => {
      this.appConfigContext.applicationConfiguration.name = v;
    });
    this.formGroup.addControl('$appCfgName', appNameCtrl);

    const gracePeriodCtrl = new FormControl();
    gracePeriodCtrl.setValidators([Validators.required, ParameterValidators.numeric]);
    gracePeriodCtrl.setValue(this.appConfigContext.applicationConfiguration.processControl.gracePeriod);
    gracePeriodCtrl.valueChanges.subscribe(v => {
      this.appConfigContext.applicationConfiguration.processControl.gracePeriod = v;
    });
    this.formGroup.addControl('$appGracePeriod', gracePeriodCtrl);

    const startType = new FormControl();
    startType.setValue(this.appConfigContext.applicationConfiguration.processControl.startType);
    startType.valueChanges.subscribe(v => {
      this.appConfigContext.applicationConfiguration.processControl.startType = v;
    });
    this.formGroup.addControl('$appStartType', startType);

    const keepAlive = new FormControl();
    keepAlive.setValue(this.appConfigContext.applicationConfiguration.processControl.keepAlive);
    keepAlive.valueChanges.subscribe(v => {
      this.appConfigContext.applicationConfiguration.processControl.keepAlive = v;
    });
    this.formGroup.addControl('$appKeepAlive', keepAlive);
  }

  /** Updates the from to ensure that all desired parameters are rendered */
  updateFormGroup() {
    for (const linkedParam of Array.from(this.linkedDescriptors.values())) {
      const descriptor = linkedParam.desc;

      // Remove control if not rendered
      if (!linkedParam.rendered) {
        this.formGroup.removeControl(descriptor.uid);
        continue;
      }

      // Do nothing if we already have the control
      if (this.formGroup.controls[descriptor.uid]) {
        continue;
      }

      const fromControl = this.createFormControl(linkedParam);
      this.formGroup.addControl(descriptor.uid, fromControl);
    }
  }

  /** Creates a control representing a single parameter  */
  createFormControl(linkedPara: LinkedParameter) {
    const descriptor = linkedPara.desc;
    const config = linkedPara.conf;
    const control = new FormControl();

    // Validation of parameters
    const validators: ValidatorFn[] = [];

    // Mandatory in case of a boolean parameter controls
    // whether or not it is shown by default in the UI
    // NO validation of it's value will be performed.
    const type = descriptor.type;
    if (descriptor.mandatory && type !== ParameterType.BOOLEAN) {
      validators.push(Validators.required);
    }
    if (descriptor.type === ParameterType.NUMERIC) {
      validators.push(ParameterValidators.numeric);
    }
    control.setValidators(validators);

    // Set default value if we have a configuration
    if (type === ParameterType.BOOLEAN) {
      control.setValue(config.value === 'true');
    } else {
      control.setValue(config.value);
    }

    // Disable in case of a fixed parameter
    if (descriptor.fixed) {
      control.disable();
    }

    // Track value changes and update
    control.valueChanges.subscribe(v => {
      linkedPara.preRender(this.appService, v);
    });

    control.updateValueAndValidity();
    return control;
  }

  /** Opens the dialog manage custom parameters */
  manageCustomParameters(): void {
    this.matDialog
      .open(ApplicationEditManualComponent, {
        width: '50%',
        height: '60%',
        minWidth: '470px',
        minHeight: '550px',
        data: cloneDeep(this.getCustomParameters()),
      })
      .afterClosed()
      .subscribe(results => {
        if (!results) {
          return;
        }
        this.updateCustomParameters(results);
      });
  }
  /** Opens the dialog manage optional parameters */
  manageOptionalParameters(groupName: string) {
    const config = new MatDialogConfig();
    config.width = '50%';
    config.height = '60%';
    config.minWidth = '470px';
    config.minHeight = '550px';
    config.data = cloneDeep(this.getOptionalParameters(groupName));
    this.matDialog
      .open(ApplicationEditOptionalComponent, config)
      .afterClosed()
      .subscribe(results => {
        if (!results) {
          return;
        }
        this.updateOptionalParams(results);
      });
  }

  /** Updates the visibility state of the optional parameters */
  updateOptionalParams(results: LinkedParameter[]) {
    for (const param of results) {
      const linkedPara = this.linkedDescriptors.get(param.desc.uid);
      // init param value if param was added
      if (!linkedPara.rendered && param.rendered) {
        this.initOptionalParameterValue(linkedPara);
      }
      linkedPara.rendered = param.rendered;
    }

    // Update order and group
    this.updateAppCfgParameterOrder();
    this.updateFormGroup();
  }

  /** Initializes the value of an optional parameter */
  initOptionalParameterValue(param: LinkedParameter) {
    const apps: ApplicationConfiguration[] = this.appService.getAllApps(this.processConfig);
    param.conf.value = this.appService.getParameterValue(param.desc, apps);
  }

  /** Updates the visibility state of custom parameters */
  updateCustomParameters(results: CustomParameter[]) {
    const existing = this.getCustomParameters().customParameters;
    const added = results.filter(a => existing.findIndex(b => a.uid === b.uid) === -1);
    const removed = existing.filter(a => results.findIndex(b => a.uid === b.uid) === -1);
    const updated = results.filter(a => existing.findIndex(b => a.uid === b.uid) !== -1);

    // Remove elements that are not existing any more
    for (const cp of removed) {
      this.removeCustomParameter(cp.uid);
    }

    // Add new element to the array
    for (const cp of added) {
      const lp = this.insertCustomParameter(cp.uid, cp.predecessorUid);

      lp.desc = cloneDeep(EMPTY_PARAMETER_DESCRIPTOR);
      lp.desc.uid = cp.uid;
      lp.desc.name = cp.uid;
      lp.desc.mandatory = true;
      lp.desc.groupName = GroupNames.CUSTOM_PARAMETERS;

      lp.conf = cloneDeep(EMPTY_PARAMETER_CONFIGURATION);
      lp.conf.uid = cp.uid;
    }

    // Update existing elements
    for (const cp of updated) {
      this.reLinkCustomParameter(cp.uid, cp.predecessorUid);
    }

    // Update order and group
    this.updateAppCfgParameterOrder();
    this.updateFormGroup();
  }

  /**
   * Inserts the given custom parameter into the parameter array.
   *
   *    A    -    B      -    C
   *
   *    A ... predecessor of the new element
   *    B ... element to be added
   *    C ... successor of the new element
   *
   */
  insertCustomParameter(paraUid: string, predecessorId: string): LinkedParameter {
    const b = new LinkedParameter();
    const a = this.linkedDescriptors.get(predecessorId);
    const c = a ? a.successor : findFirstParameter(this.linkedDescriptors.values());

    // Insert B into the current chain
    b.predecessor = a;
    b.successor = c;

    // Re-Link old predecessor
    if (a) {
      a.successor = b;
    }

    // Re-Link successor
    if (c) {
      c.predecessor = b;
    }

    // Make it visible and insert into our array
    b.custom = true;
    b.rendered = true;
    this.linkedDescriptors.set(paraUid, b);
    return b;
  }

  /**
   * Removes the parameter with the given id from the parameter array.
   *
   *    A    -    B      -    C
   *
   *    A ... predecessor of the removed element
   *    B ... element to be removed
   *    C ... successor of the removed element
   *
   */
  removeCustomParameter(paraUid: string) {
    const b = this.linkedDescriptors.get(paraUid);
    if (!b) {
      return;
    }
    const a = b.predecessor;
    const c = b.successor;

    // C is now the successor of A
    if (a) {
      a.successor = c;
    }
    // A is now the predecessor of C
    if (c) {
      c.predecessor = a;
    }

    // Finally remove element
    this.linkedDescriptors.delete(paraUid);
    this.formGroup.removeControl(paraUid);
  }

  /**
   * Updates the given element to point to the new predecessor.
   *
   *    OLD: A    -    B      -    C      -     D     -     E
   *    NEW: A    -    D      -    B      -     C     -     E
   *
   *    D ... element to be updated
   *
   */
  reLinkCustomParameter(uid: string, predecessorUid: string) {
    const d = this.linkedDescriptors.get(uid);

    // Simply remove and insert at the desired position
    this.removeCustomParameter(uid);
    const updated = this.insertCustomParameter(uid, predecessorUid);
    updated.desc = d.desc;
    updated.conf = d.conf;
  }

  /** Returns a list of all optional parameters of the given group */
  getOptionalParameters(groupName: string) {
    const params: LinkedParameter[] = [];
    if (groupName === GroupNames.CUSTOM_PARAMETERS) {
      return params;
    }
    for (const linkedParaDesc of Array.from(this.linkedDescriptors.values())) {
      const paraDesc = linkedParaDesc.desc;
      if (paraDesc.groupName !== groupName) {
        continue;
      }
      if (paraDesc.mandatory) {
        continue;
      }
      params.push(linkedParaDesc);
    }
    return params;
  }

  /** Returns the data for the custom parameter maintenance dialog */
  getCustomParameters(): Context {
    const context = new Context();

    for (const linkedParaDesc of Array.from(this.linkedDescriptors.values())) {
      // Suggestions for predecessors
      const paraDesc = linkedParaDesc.desc;
      context.availableParameters.push(new NamedParameter(paraDesc.uid, paraDesc.name, paraDesc.groupName));

      // Collect all already defined parameters
      if (!linkedParaDesc.custom) {
        continue;
      }
      const cp = new CustomParameter();
      cp.name = paraDesc.name;
      cp.uid = paraDesc.uid;
      if (linkedParaDesc.predecessor) {
        cp.predecessorUid = linkedParaDesc.predecessor.desc.uid;
      } else {
        cp.predecessorUid = '';
      }
      context.customParameters.push(cp);
    }
    return context;
  }

  /** Restores the default value of the parameter */
  revertValue(param: LinkedParameter) {
    const control = this.formGroup.controls[param.desc.uid];
    control.setValue(param.desc.defaultValue);
  }

  /** Opens a modal overlay popup showing the given template */
  openOverlay(param: ParameterDescriptor, relative: MatButton, template: TemplateRef<any>) {
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
        .withViewportMargin(30)
        .withDefaultOffsetX(37)
        .withDefaultOffsetY(-10),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: true,
      backdropClass: 'info-backdrop',
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

  /**
   * Updates the order of the parameter in the start command. To be called whenever a parameter is added / removed.
   */
  updateAppCfgParameterOrder() {
    // Clear current parameters
    const parameters = this.appConfigContext.applicationConfiguration.start.parameters;
    parameters.splice(0, parameters.length);

    // add all parameters in their defined order
    let next = findFirstParameter(this.linkedDescriptors.values());
    while (next) {
      if (!next.rendered) {
        next = next.successor;
        continue;
      }
      parameters.push(next.conf);
      next = next.successor;
    }
  }

  /** Opens a bottom sheet displaying the command-line that will be executed */
  openCommandLinePreview() {
    this.bottomSheet.open(ApplicationEditCommandPreviewComponent, {
      data: {
        commandLinePreview: this.getCommandLinePreview(),
      },
    });
  }

  /**
   * Updates the preview property to show the user what will be executed
   */
  getCommandLinePreview() {
    const preview = [];
    preview.push(this.appConfigContext.applicationConfiguration.start.executable);

    // add all parameters in their defined order
    for (const para of this.appConfigContext.applicationConfiguration.start.parameters) {
      // Special handling for boolean parameters.
      const linkedPara = this.linkedDescriptors.get(para.uid);
      if (!linkedPara.addToCommandLine(para.value)) {
        continue;
      }
      const value = linkedPara.getCommandLinePreview(this.appService, para.preRendered);
      preview.push(value);
    }
    return preview;
  }

  getSupportedStartTypes() {
    const types = this.appDesc.processControl.supportedStartTypes;
    if (!types) {
      return [ApplicationStartType.MANUAL];
    }

    // Add manual if just instance is present
    const instanceIdx = types.indexOf(ApplicationStartType.INSTANCE);
    const manualIdx = types.indexOf(ApplicationStartType.MANUAL);
    if (instanceIdx !== -1 && manualIdx === -1) {
      types.push(ApplicationStartType.MANUAL);
    }
    return types;
  }

  /** Removes the given unknown parameter */
  removeUnknownParameter(paramUid: string) {
    const idx = this.unknownParameters.findIndex(v => v.config.uid === paramUid);
    this.unknownParameters.splice(idx, 1);
  }

  /** Converts the given unknown parameter to a custom parameter */
  covertUnknownToCustomParameter(paramUid: string) {
    const param = this.unknownParameters.find(up => up.config.uid === paramUid);

    // New custom will be added as last one
    const newUid = paramUid + '.custom';
    const lastParam = findLastParameter(this.linkedDescriptors.values());
    const newParam = this.insertCustomParameter(newUid, lastParam.desc.uid);

    // render the parameter according to the descriptor
    const value = this.appService.preRenderParameter(param.descriptor, param.config.value);

    // Setup descriptor and value accordingly
    // We are using a new UID to avoid conflicts when changing the version again
    newParam.desc = cloneDeep(EMPTY_PARAMETER_DESCRIPTOR);
    newParam.desc.uid = newUid;
    newParam.desc.name = newUid;
    newParam.desc.mandatory = true;
    newParam.desc.groupName = GroupNames.CUSTOM_PARAMETERS;

    newParam.conf = cloneDeep(EMPTY_PARAMETER_CONFIGURATION);
    newParam.conf.uid = newUid;
    newParam.conf.value = value.join(' ');
    newParam.conf.preRendered = param.config.preRendered;

    // Remove from the list of undefined
    this.removeUnknownParameter(paramUid);
    this.updateAppCfgParameterOrder();
    this.updateFormGroup();
  }
}
