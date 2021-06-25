import { Injectable } from '@angular/core';
import { isEqual } from 'lodash-es';
import {
  ApplicationConfiguration,
  ApplicationDescriptor,
  ApplicationType,
  CommandConfiguration,
  EndpointsConfiguration,
  ExecutableDescriptor,
  HttpEndpoint,
  InstanceConfiguration,
  OperatingSystem,
  ParameterConfiguration,
  ParameterDescriptor,
  ParameterType,
  ProcessControlConfiguration,
} from 'src/app/models/gen.dtos';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';

/** The type of a difference, typically propagated from simple attributes to complex diffs. */
export enum DiffType {
  NOT_IN_BASE = 'not-in-base',
  NOT_IN_COMPARE = 'not-in-compare',
  CHANGED = 'changed',
  UNCHANGED = 'unchanged',
}

/** Returns the DiffType for an object, comparing the two given instances in depth */
function getChangeType(base: any, compare: any): DiffType {
  const noBase = base === null || base === undefined;
  const noCompare = compare === null || compare === undefined;

  if (noBase && !noCompare) {
    return DiffType.NOT_IN_BASE;
  } else if (noCompare && !noBase) {
    return DiffType.NOT_IN_COMPARE;
  } else if (isEqual(base, compare)) {
    return DiffType.UNCHANGED;
  } else {
    return DiffType.CHANGED;
  }
}

/** Given two complex objects and the DiffTypes of all the contained child objects, calculate a "outer" DiffType for the complex object */
function getParentChangeType(base: any, compare: any, ...types: DiffType[]): DiffType {
  const noBase = base === null || base === undefined;
  const noCompare = compare === null || compare === undefined;
  if (noBase && !noCompare) {
    return DiffType.NOT_IN_BASE;
  } else if (noCompare && !noBase) {
    return DiffType.NOT_IN_COMPARE;
  }

  // changed if there is a single diff !== UNCHANGED.
  return types.every((e) => e === DiffType.UNCHANGED || e === undefined) ? DiffType.UNCHANGED : DiffType.CHANGED;
}

/** Maintains information about the difference in an object. The given value is the base value is set, otherwise the compare value. */
export class Difference {
  /** The value to display to the user */
  public value: any;

  /** The type of difference to render */
  public type: DiffType;

  constructor(private base: any, private compare: any, valueOverride?: any) {
    this.value = !!valueOverride ? valueOverride : base === null || base === undefined ? compare : base;
    this.type = getChangeType(base, compare);
  }
}

/** Differences in the ProcessControlConfiguration of an Application */
export class ProcessControlDiff {
  public type: DiffType;

  public startType: Difference;
  public keepAlive: Difference;
  public noOfRetries: Difference;
  public gracePeriod: Difference;
  public attachStdin: Difference;

  constructor(base: ProcessControlConfiguration, compare: ProcessControlConfiguration) {
    this.startType = new Difference(base?.startType, compare?.startType);
    this.keepAlive = new Difference(base?.keepAlive, compare?.keepAlive);
    this.noOfRetries = new Difference(base?.noOfRetries, compare?.noOfRetries);
    this.gracePeriod = new Difference(base?.gracePeriod, compare?.gracePeriod);
    this.attachStdin = new Difference(base?.attachStdin, compare?.attachStdin);

    this.type = getParentChangeType(
      base,
      compare,
      this.startType.type,
      this.keepAlive.type,
      this.noOfRetries.type,
      this.gracePeriod.type,
      this.attachStdin.type
    );
  }
}

/** Differences in one of the CommandConfigurations in an ApplicationConfiguration */
export class CommandDiff {
  public type: DiffType;
  public executable: Difference;
  public parameters: ParameterDiff[] = [];

  constructor(base: CommandConfiguration, compare: CommandConfiguration, baseDescriptor: ExecutableDescriptor) {
    this.executable = new Difference(base?.executable, compare?.executable);

    if (!!base && !!base.parameters?.length) {
      for (const param of base.parameters) {
        const compareParam = compare?.parameters?.find((p) => p.uid === param.uid);
        this.parameters.push(
          new ParameterDiff(
            param,
            compareParam,
            baseDescriptor?.parameters?.find((p) => p.uid === param.uid)
          )
        );
      }
    }
    let newParamChange: DiffType = DiffType.UNCHANGED;
    if (!!compare && !!compare.parameters?.length) {
      for (const param of compare.parameters) {
        const baseParam = base?.parameters?.find((p) => p.uid === param.uid);
        if (!baseParam) {
          newParamChange = DiffType.CHANGED;
        }
      }
    }

    this.type = getParentChangeType(base, compare, this.executable.type, newParamChange, ...this.parameters.map((p) => p.type));
  }
}

/** Differences in a single parameter of a CommandConfiguration. Creates diffs for every pre-rendered part of the parameter if possible */
export class ParameterDiff {
  public type: DiffType;
  public values: Difference[] = [];

  constructor(base: ParameterConfiguration, compare: ParameterConfiguration, public descriptor: ParameterDescriptor) {
    // in case we KNOW this is a PASSWORD parameter, we want to pre-mask the value in each actual value.
    const maskingValue = descriptor?.type === ParameterType.PASSWORD ? (base?.value === null || base?.value === undefined ? compare?.value : base.value) : null;

    if (!!base?.preRendered?.length) {
      if (base.preRendered.length !== compare?.preRendered?.length) {
        // DIFFERENT length, we cannot *directly* compare the values.
        for (const val of base.preRendered) {
          const masked = !!maskingValue ? val.replace(maskingValue, '*'.repeat(maskingValue.length)) : val;
          this.values.push(new Difference(val, compare?.value, masked));
        }
      } else {
        for (let i = 0; i < base.preRendered.length; ++i) {
          const masked = !!maskingValue ? base.preRendered[i].replace(maskingValue, '*'.repeat(maskingValue.length)) : base.preRendered[i];
          this.values.push(new Difference(base.preRendered[i], compare.preRendered[i], masked));
        }
      }
    } else if (!!compare?.preRendered?.length) {
      // we don't have anything, but they got some.
      for (const compareVal of compare.preRendered) {
        const masked = !!maskingValue ? compareVal.replace(maskingValue, '*'.repeat(maskingValue.length)) : compareVal;
        this.values.push(new Difference(null, compareVal, masked));
      }
    }

    this.type = getParentChangeType(base, compare, ...this.values.map((d) => d.type));
  }
}

/** Differences in declared EndpointsConfiguration of an ApplicationConfiguration */
export class EndpointsDiff {
  public type: DiffType;
  public http: HttpEndpointDiff[] = [];

  constructor(base: EndpointsConfiguration, compare: EndpointsConfiguration) {
    if (!!base && !!base.http?.length) {
      for (const baseHttp of base.http) {
        const compareHttp = compare?.http?.find((p) => p.id === baseHttp.id);
        this.http.push(new HttpEndpointDiff(baseHttp, compareHttp));
      }
    }
    if (!!compare && !!compare.http?.length) {
      for (const compareHttp of compare.http) {
        if (!base?.http?.find((p) => p.id === compareHttp.id)) {
          this.http.push(new HttpEndpointDiff(null, compareHttp));
        }
      }
    }
    this.type = getParentChangeType(base, compare, ...this.http.map((d) => d.type));
  }
}

/** Differences in a single HttpEndpoint */
export class HttpEndpointDiff {
  public type: DiffType;

  public path: Difference;
  public port: Difference;
  public secure: Difference;
  public trustAll: Difference;
  public trustStore: Difference;
  public trustStorePass: Difference;
  public authType: Difference;
  public authUser: Difference;
  public authPass: Difference;

  constructor(base: HttpEndpoint, compare: HttpEndpoint) {
    this.path = new Difference(base?.path, compare?.path);
    this.port = new Difference(base?.port, compare?.port);
    this.secure = new Difference(base?.secure, compare?.secure);
    this.trustAll = new Difference(base?.trustAll, compare?.trustAll);
    this.trustStore = new Difference(base?.trustStore, compare?.trustStore);
    this.trustStorePass = new Difference(base?.trustStorePass, compare?.trustStorePass);
    this.authType = new Difference(base?.authType, compare?.authType);
    this.authUser = new Difference(base?.authUser, compare?.authUser);
    this.authPass = new Difference(base?.authPass, compare?.authPass);

    this.type = getParentChangeType(
      base,
      compare,
      this.path.type,
      this.port.type,
      this.secure.type,
      this.trustAll.type,
      this.trustStore.type,
      this.trustStorePass.type,
      this.authType.type,
      this.authUser.type,
      this.authPass.type
    );
  }
}

/** All differences between two ApplicationConfigurations */
export class ApplicationConfigurationDiff {
  public type: DiffType;

  public uid: Difference;
  public name: Difference;
  public processControl: ProcessControlDiff;
  public start: CommandDiff;
  public endpoints: EndpointsDiff;
  public os: OperatingSystem;

  constructor(base: ApplicationConfiguration, compare: ApplicationConfiguration, public descriptor: ApplicationDescriptor) {
    this.uid = new Difference(base?.uid, compare?.uid);
    this.name = new Difference(base?.name, compare?.name);
    if (descriptor?.type !== ApplicationType.CLIENT) {
      this.processControl = new ProcessControlDiff(base?.processControl, compare?.processControl);
    }
    this.start = new CommandDiff(base?.start, compare?.start, descriptor?.startCommand);
    this.endpoints = new EndpointsDiff(base?.endpoints, compare?.endpoints);
    this.type = getParentChangeType(base, compare, this.name.type, this.processControl?.type, this.start.type, this.endpoints.type);

    this.os = base === null || base === undefined ? getAppOs(compare.application) : getAppOs(base.application);
  }
}

/** Differences in the configuration between two InstanceConfigurations */
export class InstanceConfigurationDiff {
  public type: DiffType;

  public name: Difference;
  public description: Difference;
  public autoStart: Difference;
  public purpose: Difference;
  public productTag: Difference;
  public configTree: Difference;
  public autoUninstall: Difference;

  constructor(base: InstanceConfiguration, compare: InstanceConfiguration) {
    this.name = new Difference(base?.name, compare?.name);
    this.description = new Difference(base?.description, compare?.description);
    this.autoStart = new Difference(base?.autoStart, compare?.autoStart);
    this.purpose = new Difference(base?.purpose, compare?.purpose);
    this.productTag = new Difference(base?.product?.tag, compare?.product?.tag);
    this.configTree = new Difference(base?.configTree?.id, compare?.configTree?.id);
    this.autoUninstall = new Difference(base?.autoUninstall, compare?.autoUninstall);

    this.type = getParentChangeType(
      base,
      compare,
      this.name.type,
      this.description.type,
      this.autoStart.type,
      this.purpose.type,
      this.productTag.type,
      this.configTree.type,
      this.autoUninstall.type
    );
  }
}

@Injectable({
  providedIn: 'root',
})
export class HistoryDiffService {
  constructor() {}

  public diffAppConfig(base: ApplicationConfiguration, compare: ApplicationConfiguration, baseDescriptor: ApplicationDescriptor): ApplicationConfigurationDiff {
    return new ApplicationConfigurationDiff(base, compare, baseDescriptor);
  }

  public diffInstanceConfig(base: InstanceConfiguration, compare: InstanceConfiguration): InstanceConfigurationDiff {
    return new InstanceConfigurationDiff(base, compare);
  }
}
