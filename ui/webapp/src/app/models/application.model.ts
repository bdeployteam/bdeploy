import { ApplicationService } from '../services/application.service';
import { ApplicationDto, ApplicationType, ManifestKey, OperatingSystem, ParameterConfiguration, ParameterDescriptor, ParameterType } from './gen.dtos';

/**
 * Default group names used in the application
 */
export class GroupNames {
  public static readonly CUSTOM_PARAMETERS = 'Custom Parameters';
  public static readonly UNGROUPED_PARAMETERS = 'Ungrouped Parameters';
}

/**
 * Represents the definition and runtime configuration of a parameter. The predecessor
 * and the successor are stored so that the correct order of a parameter is always known.
 */
export class LinkedParameter {
  /**
   * The definition of the parameter
   */
  desc: ParameterDescriptor;

  /**
   * The value of the parameter
   */
  conf: ParameterConfiguration;

  /**
   * Parameter is visible in the UI
   */
  rendered: boolean;

  /**
   * Parameter is user-defined
   */
  custom: boolean;

  /**
   * Our successor
   */
  successor: LinkedParameter;

  /**
   * Our predecessor
   */
  predecessor: LinkedParameter;

  /**
   * Creates a new parameter by defining its descriptor
   *
   * @param desc the description of the parameter
   * @param custom parameter is defined by the user
   */
  constructor(desc?: ParameterDescriptor, custom?: boolean) {
    this.desc = desc;
    this.custom = custom;
  }

  /**
   * Updates the value of the parameter configuration and renders it according to the definition.
   *
   * @param service the service to format the parameter
   * @param value the actual value of the parameter
   * @return the concatenated value
   */
  public preRender(service: ApplicationService, value: any): string {
    // update value
    if (this.desc.type === ParameterType.BOOLEAN) {
      this.conf.value = String(value);
    } else {
      this.conf.value = value ? String(value) : '';
    }

    // Render command line part
    if (this.custom) {
      this.conf.preRendered = [this.conf.value];
    } else {
      this.conf.preRendered = service.preRenderParameter(this.desc, this.conf.value);
    }
    return this.conf.preRendered.join(' ');
  }

  /**
   * Returns whether or not this parameter should be added to the final command line
   */
  public addToCommandLine(value: string) {
    // We need special handling for booleans
    if (this.desc.type !== ParameterType.BOOLEAN) {
      return true;
    }

    // HasValue=false; Mandatory=true: Mandatory parameter, value slider -> Value slider controls whether parameter is present on command line
    if (!this.desc.hasValue && this.desc.mandatory) {
      return value === 'true';
    }

    return true;
  }

  /**
   * Returns a preview of this command that is shown to the user
   *
   * @param service the service to format the parameter
   * @param value the actual value of the parameter
   * @return the preview of the command line representation
   */
  public getCommandLinePreview(service: ApplicationService, value: string[]) {
    // In case of a password parameter we mask the actual value
    if (this.custom || this.desc.type !== ParameterType.PASSWORD) {
      return value.join(' ');
    }

    // We ask the service to render the preview again but with a fake value
    value = service.preRenderParameter(this.desc, '****');
    return value.join(' ');
  }
}

/**
 * Represents a custom parameter defined by the user
 */
export class CustomParameter {
  uid: string;
  name: string;
  predecessorUid: string;
}

/**
 * Simplified parameter having a unique ID and a name
 */
export class NamedParameter {
  uid: string;
  name: string;
  group: string;

  constructor(uid: string, name: string, group: string) {
    this.uid = uid;
    this.name = name;
    this.group = group;
  }
}

/** Represents a single application that is available for multiple operating systems  */
export class ApplicationGroup {
  /** The list of supported applications */
  public operatingSystems: OperatingSystem[] = [];

  /** the list of contained applications */
  public applications: ApplicationDto[] = [];

  /** The human readable name */
  public appName: string;

  /** The name of the application key without OS suffix */
  public appKeyName: string;

  /** The tag of the application key */
  public appKeyTag: string;

  /** The type of the applications */
  public appType: ApplicationType;

  /**
   * Returns the base name of the application manifest key.
   */
  public static getAppKeyName(appKey: ManifestKey) {
    const fullName = appKey.name;
    const lastSlashIdx = fullName.lastIndexOf('/');
    return fullName.substring(0, lastSlashIdx);
  }

  /**
   * Returns the OS supported by this application
   */
  public static getAppOs(appKey: ManifestKey): OperatingSystem {
    const fullName = appKey.name;
    const lastSlashIdx = fullName.lastIndexOf('/') + 1;
    const osName = fullName.substring(lastSlashIdx).toUpperCase();
    return OperatingSystem[osName];
  }

  /**
   * Adds a new application to this list. Must have the same base name.
   */
  public add(dto: ApplicationDto) {
    const keyName = ApplicationGroup.getAppKeyName(dto.key);
    const os = ApplicationGroup.getAppOs(dto.key);

    // Name must match the previous ones
    if (this.applications.length === 0) {
      this.appType = dto.descriptor.type;
      this.appKeyTag = dto.key.tag;
      this.appKeyName = keyName;
      this.appName = dto.name;
    } else {
      if (this.appKeyName !== keyName) {
        throw new Error('Applications must have the same base name.');
      }
      if (this.appType !== dto.descriptor.type) {
        throw new Error('Applications must have the same type.');
      }
    }
    this.applications.push(dto);
    this.operatingSystems.push(os);
  }

  /**
   * Returns the application that supports the given OS
   */
  public getAppFor(os: OperatingSystem): ApplicationDto {
    return this.applications.find(app => {
      const appOs = ApplicationGroup.getAppOs(app.key);
      return appOs === os;
    });
  }

  /**
   * Returns whether or not the contained applications are client apps
   */
  public isClientApp(): boolean {
    return this.appType === ApplicationType.CLIENT;
  }

  /**
   * Returns whether or not the contained applications are server apps
   */
  public isServerApp(): boolean {
    return this.appType === ApplicationType.SERVER;
  }
}
