import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash';
import { Observable } from 'rxjs';
import { CLIENT_NODE_NAME, EMPTY_COMMAND_CONFIGURATION, EMPTY_PARAMETER_CONFIGURATION } from '../models/consts';
import { ApplicationConfiguration, ApplicationDescriptor, ApplicationDto, ApplicationType, InstanceNodeConfigurationDto, ManifestKey, ParameterConfiguration, ParameterDescriptor, ParameterType } from '../models/gen.dtos';
import { ProcessConfigDto } from '../models/process.model';
import { ConfigService } from './config.service';
import { InstanceGroupService } from './instance-group.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root',
})
export class ApplicationService {
  private readonly log: Logger = this.loggingService.getLogger('ApplicationService');

  /** The validation state. Key=UID of the application. Value=List of errors */
  private readonly validationStates = new Map<string, string[]>();

  /** The modification state. Key=UID of the application. Value=dirty state */
  private readonly dirtyStates = new Map<string, boolean>();

  // Overall dirty state of the underlying data structure. Detects app-reordering
  private dirtyState = false;

  /** Keeps track of applications which are now missing */
  private readonly missingApps: ManifestKey[] = [];

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
    private groupService: InstanceGroupService,
  ) {}

  public listApplications(instanceGroupName: string, product: ManifestKey): Observable<ApplicationDto[]> {
    const url: string = this.buildAppUrl(instanceGroupName, product);
    this.log.debug('listApplications: ' + url);
    return this.http.get<ApplicationDto[]>(url);
  }

  public getDescriptor(
    instanceGroupName: string,
    productKey: ManifestKey,
    appKey: ManifestKey,
  ): Observable<ApplicationDescriptor> {
    const url = this.buildAppNameTagUrl(instanceGroupName, productKey, appKey) + '/descriptor';
    this.log.debug('getDescriptor: ' + url);
    return this.http.get<ApplicationDescriptor>(url);
  }

  public createUuid(name: string): Observable<string> {
    return this.groupService.createUuid(name);
  }

  private buildAppNameTagUrl(instanceGroupName: string, productKey: ManifestKey, appKey: ManifestKey): string {
    return this.buildAppUrl(instanceGroupName, productKey) + '/' + appKey.name + '/' + appKey.tag;
  }

  private buildAppUrl(instanceGroupName: string, productKey: ManifestKey): string {
    return this.buildProductUrl(instanceGroupName, productKey) + '/application';
  }

  private buildProductUrl(instanceGroupName: string, productKey: ManifestKey): string {
    return this.buildGroupUrl(instanceGroupName) + '/product/' + productKey.name + '/' + productKey.tag;
  }

  private buildGroupUrl(instanceGroupName: string): string {
    return this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + instanceGroupName;
  }

  /**
   * Updates the global parameters of all applications according to the given template.
   */
  public updateGlobalParameters(
    appDesc: ApplicationDescriptor,
    template: ApplicationConfiguration,
    processConfig: ProcessConfigDto,
  ) {
    // Build index for quick parameter lookup
    const paraDescMap = new Map<string, ParameterDescriptor>();
    for (const paraDesc of appDesc.startCommand.parameters) {
      paraDescMap.set(paraDesc.uid, paraDesc);
    }
    const paraValues = new Map<string, any>();
    for (const paraValue of template.start.parameters) {
      paraValues.set(paraValue.uid, paraValue.value);
    }

    // Process start and stop command of all apps
    const apps = this.getAllApps(processConfig);
    for (const app of apps) {
      // Do not update ourself. Changes are already applied
      if (app.uid === template.uid) {
        continue;
      }

      // Update start command
      for (const param of app.start.parameters) {
        const paraDef = paraDescMap.get(param.uid);
        if (!paraDef || !paraDef.global) {
          continue;
        }
        const globalValue = paraValues.get(param.uid);
        if (!globalValue) {
          continue;
        }
        param.value = globalValue;
        param.preRendered = this.preRenderParameter(paraDef, param.value);
      }

      // Update stop command. Update all values not only global ones
      // Stop command is not yet configurable
      for (const param of app.stop.parameters) {
        const paraDef = paraDescMap.get(param.uid);
        const globalValue = paraValues.get(param.uid);
        if (!globalValue) {
          continue;
        }
        param.value = globalValue;
        param.preRendered = this.preRenderParameter(paraDef, param.value);
      }
    }
  }

  /**
   * Initializes the given configuration according to the given descriptor and template.
   */
  public initAppConfig(
    appConfig: ApplicationConfiguration,
    appDesc: ApplicationDescriptor,
    templates: ApplicationConfiguration[],
  ) {
    // Initialize name
    appConfig.name = appDesc.name;
    appConfig.processControl = {
      startType: appDesc.processControl.supportedStartTypes[0],
      keepAlive: appDesc.processControl.supportsKeepAlive,
      gracePeriod: appDesc.processControl.gracePeriod,
      noOfRetries: appDesc.processControl.noOfRetries,
    };

    // Initialize start command
    appConfig.start = cloneDeep(EMPTY_COMMAND_CONFIGURATION);
    if (appDesc.startCommand) {
      appConfig.start.executable = appDesc.startCommand.launcherPath;
      appConfig.start.parameters = this.createParameters(appDesc.startCommand.parameters, templates);
    }

    // Initialize stop command
    appConfig.stop = cloneDeep(EMPTY_COMMAND_CONFIGURATION);
    if (appDesc.stopCommand) {
      appConfig.stop.executable = appDesc.stopCommand.launcherPath;
      appConfig.stop.parameters = this.createParameters(appDesc.stopCommand.parameters, templates);
    }
  }

  /** Creates and returns an array of parameters based on the given descriptors */
  createParameters(descs: ParameterDescriptor[], templates: ApplicationConfiguration[]): ParameterConfiguration[] {
    const configs: ParameterConfiguration[] = [];

    // Prepare all mandatory parameters
    for (const paraDesc of descs) {
      if (!paraDesc.mandatory) {
        continue;
      }
      const config = this.createParameter(paraDesc, templates);
      configs.push(config);
    }
    return configs;
  }

  /**
   * Creates and returns a new parameter based on the given descriptor
   */
  createParameter(paraDesc: ParameterDescriptor, templates: ApplicationConfiguration[]) {
    const config = cloneDeep(EMPTY_PARAMETER_CONFIGURATION);
    config.uid = paraDesc.uid;
    this.updateParameterValue(config, paraDesc, templates);
    return config;
  }

  /**
   * Updates the value of the given paramter based on the given descriptor.
   * Takes the global value if the parameter is defined as global and otherwise the default value.
   */
  updateParameterValue(
    config: ParameterConfiguration,
    paraDesc: ParameterDescriptor,
    templates: ApplicationConfiguration[],
  ) {
    config.value = this.getParameterValue(paraDesc, templates);
    config.preRendered = this.preRenderParameter(paraDesc, config.value);
  }

  /** Returns the default value for the given parameter */
  getParameterValue(paraDesc: ParameterDescriptor, templates: ApplicationConfiguration[]): string {
    // Take default value for a non-global parameter
    if (!paraDesc.global || !templates) {
      return paraDesc.defaultValue;
    }

    for (const template of templates) {
      let para = template.start.parameters.find(p => p.uid === paraDesc.uid);
      if (!para) {
        para = template.stop.parameters.find(p => p.uid === paraDesc.uid);
      }
      if (para) {
        return para.value;
      }
    }
    return paraDesc.defaultValue;
  }

  /**
   * Renders the given parameter value according to the definition.
   *
   * __Implementation note:__ Changing the logic in here also requires adoptions
   * to the Java code located in the _iface.ParameterDescriptor_ class in the backend.
   *
   * @param desc the definition of the parameter
   * @param value the actual value of the parameter
   * @return the concatenated value
   */
  public preRenderParameter(desc: ParameterDescriptor, value: any): string[] {
    const strValue = value ? value : '';
    if (desc.hasValue) {
      if (desc.valueAsSeparateArg) {
        return [desc.parameter, strValue];
      }
      return [desc.parameter + desc.valueSeparator + strValue];
    }
    return [desc.parameter];
  }

  /**
   * Validates all applications of all nodes.
   */
  public validate(processConfig: ProcessConfigDto) {
    const nodeListDto = processConfig.nodeList;

    // Clear old state so that removed apps does not remain invalid
    this.validationStates.clear();
    this.missingApps.splice(0, this.missingApps.length);

    // Re-Validate all nodes
    for (const node of nodeListDto.nodeConfigDtos) {
      this.validateNode(node, nodeListDto.applications);
    }

    // Trace some infos for debugging
    if (this.isAllValid()) {
      this.log.debug('No errors detected. All nodes are valid.');
    } else {
      this.log.debug('One or more nodes are invalid.');
    }
  }

  /**
   * Validates all applications of the given node.
   */
  public validateNode(nodeConfigDto: InstanceNodeConfigurationDto, apps: { [index: string]: ApplicationDescriptor }) {
    const nodeConfig = nodeConfigDto.nodeConfiguration;
    if (!nodeConfig) {
      return;
    }
    for (const appCfg of nodeConfig.applications) {
      if (!appCfg.uid) {
        continue;
      }
      const appDesc = apps[appCfg.application.name];

      if (!appDesc) {
        this.missingApps.push(appCfg.application);
        continue;
      }

      const isClientNode = nodeConfigDto.nodeName === CLIENT_NODE_NAME;

      const errors = this.validateApp(isClientNode, appCfg, appDesc);
      if (errors.length === 0) {
        this.validationStates.delete(appCfg.uid);
        this.log.debug('Application ' + appCfg.uid + ' is valid. Resetting error flag.');
      } else {
        this.validationStates.set(appCfg.uid, errors);
        this.log.debug(
          'Application ' + appCfg.uid + ' is invalid. Setting error flag. Errors: \n\t' + errors.join('\n'),
        );
      }
    }
  }

  /**
   * Validates if all mandatory parameters are configured and that they have the correct type.
   */
  public validateApp(isClientNode: boolean, cfg: ApplicationConfiguration, desc: ApplicationDescriptor) {
    const errors: string[] = [];

    // Check application type (handle wrong types like missing apps)
    const isClientApp = desc.type === ApplicationType.CLIENT;
    if (isClientNode !== isClientApp) {
      this.missingApps.push(cfg.application);
    }

    // Check if start command is valid
    if (desc.startCommand.launcherPath !== cfg.start.executable) {
      errors.push('Start executable command outdated.');
    }
    for (const paraDef of desc.startCommand.parameters) {
      const paraCfg = cfg.start.parameters.find(p => p.uid === paraDef.uid);
      const paraErrors = this.validateParam(paraCfg, paraDef);
      if (paraErrors) {
        errors.push(paraErrors);
      }
    }

    // Check if stop command is valid
    if (desc.stopCommand) {
      if (desc.stopCommand.launcherPath !== cfg.stop.executable) {
        errors.push('Stop executable command outdated.');
      }
      for (const paraDef of desc.startCommand.parameters) {
        const paraCfg = cfg.start.parameters.find(p => p.uid === paraDef.uid);
        const paraErrors = this.validateParam(paraCfg, paraDef);
        if (paraErrors) {
          errors.push(paraErrors);
        }
      }
    }
    return errors;
  }

  /**
   * Validates this parameter against its definition
   */
  public validateParam(paraCfg: ParameterConfiguration, paraDef: ParameterDescriptor) {
    // check if a mandatory parameter is missing
    if (paraDef.mandatory && (!paraCfg || !paraCfg.value)) {
      return paraDef.name + ': Parameter is mandatory but no value is configured.';
    }
    if (!paraCfg) {
      return;
    }

    // Validate value against the type
    const value = paraCfg.value;
    switch (paraDef.type) {
      case ParameterType.BOOLEAN: {
        if (value === 'true' || value === 'false') {
          return;
        }
        return paraDef.name + ': Invalid value configured. Expecting true or false but was ' + value;
      }
      case ParameterType.NUMERIC: {
        if (Number(value)) {
          return;
        }
        return paraDef.name + ': Invalid value configured. Expecting a number but was ' + value;
      }
    }
  }

  /**
   * Returns whether or not the given application is valid
   */
  public isValid(appUid: string) {
    const errors = this.validationStates.get(appUid);
    if (!errors || errors.length === 0) {
      return true;
    }
    return false;
  }

  /**
   * Returns whether or not the given application has unsaved changes
   */
  public isDirty(appUid: string) {
    return this.dirtyStates.get(appUid);
  }

  /**
   * Returns whether the given ManifestKey is part of the currently validated product
   */
  public isMissing(app: ManifestKey) {
    return this.missingApps.findIndex(a => isEqual(a, app)) !== -1;
  }

  /**
   * Returns whether or not there are is at least one validation issue.
   */
  public isAllValid() {
    if (this.missingApps.length !== 0) {
      return false;
    }

    const appIds = Array.from(this.validationStates.keys());
    for (const appId of appIds) {
      if (!this.isValid(appId)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns whether or not at least one app has local modifications
   */
  public isOneDirty() {
    return this.dirtyState;
  }

  /**
   * Calculates the dirty state of all nodes and its applications
   */
  public calculateDirtyState(processConfig: ProcessConfigDto) {
    this.dirtyStates.clear();
    this.dirtyState = false;

    // Re-Validate all nodes
    const nodeListDto = processConfig.nodeList;
    const cloned = processConfig.clonedNodeList.nodeConfigDtos;
    for (const nodeConfigDto of nodeListDto.nodeConfigDtos) {
      const clonedNodeConfigDto = cloned.find(nc => nc.nodeName === nodeConfigDto.nodeName);

      const nodeConfig = nodeConfigDto.nodeConfiguration;
      const clonedNodeConfig = clonedNodeConfigDto.nodeConfiguration;

      // NO old and new config -> All OK. Nothing to do
      if (!nodeConfig && !clonedNodeConfig) {
        continue;
      }

      // Mark all old apps as dirty when current app config is not set any more
      if (!nodeConfig && clonedNodeConfig) {
        clonedNodeConfig.applications.forEach(a => this.dirtyStates.set(a.uid, true));
        this.log.debug(nodeConfigDto.nodeName + ': Marking as dirty as all apps have been removed.');
        continue;
      }

      // Calculate which apps have been added, removed or updated
      const clonedApps = clonedNodeConfig ? clonedNodeConfig.applications : [];
      const currentApps = nodeConfig.applications;
      const added = currentApps.filter(a => clonedApps.findIndex(b => a.uid === b.uid) === -1);
      const removed = clonedApps.filter(a => currentApps.findIndex(b => a.uid === b.uid) === -1);
      const updated = currentApps.filter(a => clonedApps.findIndex(b => a.uid === b.uid) !== -1);

      // Mark all apps that have been added or removed as dirty
      for (const appCfg of removed) {
        this.dirtyStates.set(appCfg.uid, true);
        this.log.debug('Application ' + appCfg.uid + ' has been removed. Setting dirty flag.');
      }
      for (const appCfg of added) {
        if (!appCfg.uid) {
          continue;
        }
        this.dirtyStates.set(appCfg.uid, true);
        this.log.debug('Application ' + appCfg.uid + ' has been added. Setting dirty flag.');
      }

      // Calculate dirty state of existing ones
      for (const appCfg of updated) {
        const clonedAppCfg = clonedNodeConfig.applications.find(ca => ca.uid === appCfg.uid);
        const appDirty = !isEqual(appCfg, clonedAppCfg);
        this.dirtyStates.set(appCfg.uid, appDirty);
        if (appDirty) {
          this.log.debug('Application ' + appCfg.uid + ' has local changes. Setting dirty flag.');
        } else {
          this.log.debug('Application ' + appCfg.uid + ' is in sync now. Removing dirty flag.');
        }
      }
    }

    // Check if one application has some changes
    const appIds = Array.from(this.dirtyStates.keys());
    for (const appId of appIds) {
      if (this.isDirty(appId)) {
        this.dirtyState = true;
      }
    }

    // Check instance for modifications.
    if (!this.dirtyState) {
      this.dirtyState = !isEqual(processConfig.instance, processConfig.clonedInstance);
    }

    // Calculate overall dirty state of nodes. Detects app re-sorting in the same node
    if (!this.dirtyState) {
      this.dirtyState = !isEqual(processConfig.nodeList.nodeConfigDtos, processConfig.clonedNodeList.nodeConfigDtos);
    }

    // Trace some infos for debugging
    if (this.dirtyState) {
      this.log.debug('One or more nodes have unsaved local changes.');
    } else {
      this.log.debug('No changes detected. All nodes are in sync.');
    }
  }

  /**
   * Clears the dirty, missing and validation states.
   */
  public clearState() {
    this.dirtyState = false;
    this.dirtyStates.clear();
    this.validationStates.clear();
    this.missingApps.splice(0, this.missingApps.length);
  }

  /** Returns a list of all apps of the given process configuration  */
  getAllApps(configDto: ProcessConfigDto) {
    const apps: ApplicationConfiguration[] = [];
    for (const nodeConfig of configDto.nodeList.nodeConfigDtos) {
      if (!nodeConfig.nodeConfiguration) {
        continue;
      }
      for (const appCfg of nodeConfig.nodeConfiguration.applications) {
        apps.push(appCfg);
      }
    }
    return apps;
  }

  /**
   * Updates the application version of all applications and creates/updates parameters.
   * Typically called after the product version has been changed.
   *
   * @param config the current version of the apps
   * @param dtos the current version of the descriptor
   */
  updateApplications(config: ProcessConfigDto, dtos: ApplicationDto[]) {
    const descriptors: { [index: string]: ApplicationDescriptor } = {};
    const keys: { [index: string]: ManifestKey } = {};
    dtos.forEach(a => {
      descriptors[a.key.name] = a.descriptor;
      keys[a.key.name] = a.key;
    });

    // Collect all applications of all nodes
    const apps = this.getAllApps(config);
    for (const appDesc of this.getAllApps(config)) {
      const newAppManifest = keys[appDesc.application.name];

      // only set new application when found - otherwise leave the old version and leave it to validation
      // to detect that this application is no longer contained in the product.
      if (!newAppManifest) {
        continue;
      }

      // Update reference to the new version
      const newAppDescriptor: ApplicationDescriptor = descriptors[newAppManifest.name];
      appDesc.application = newAppManifest;
      this.updateApplicationParams(appDesc, newAppDescriptor, apps);
    }

    config.nodeList.applications = descriptors;
    config.setApplications(dtos);
  }

  /**
   * Updates the parameters of the given application according to the given descriptor.
   */
  updateApplicationParams(
    app: ApplicationConfiguration,
    desc: ApplicationDescriptor,
    templates: ApplicationConfiguration[],
  ) {
    if (desc.startCommand) {
      app.start.executable = desc.startCommand.launcherPath;
      this.updateParameter(app.start.parameters, desc.startCommand.parameters, templates);
    }
    if (desc.stopCommand) {
      app.stop.executable = desc.stopCommand.launcherPath;
      this.updateParameter(app.stop.parameters, desc.stopCommand.parameters, templates);
    }
  }

  /**
   * Updates the configuration according to the given descriptors. The following is done:
   *
   * Create missing mandatory parameters.
   * Update fixed parameters to the newest value.
   * Set global parameters
   */
  updateParameter(
    configs: ParameterConfiguration[],
    descs: ParameterDescriptor[],
    templates: ApplicationConfiguration[],
  ) {
    // Order of parameters is important. Thus we need to insert a missing parameter
    // at the correct index in the config array.
    for (let index = 0; index < descs.length; index++) {
      const desc = descs[index];

      // Create parameter if it is not yet defined but mandatory
      let config = configs.find(c => c.uid === desc.uid);
      if (!config && desc.mandatory) {
        config = this.createParameter(desc, templates);
        configs.splice(index, 0, config);
        continue;
      }
      // Ignore if parameter is not yet defined
      if (!config) {
        continue;
      }

      // Overwrite value if parameter is fixed
      if (desc.fixed) {
        this.updateParameterValue(config, desc, templates);
      }
    }
  }
}
