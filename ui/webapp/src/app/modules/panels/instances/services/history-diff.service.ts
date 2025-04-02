import { Injectable } from '@angular/core';
import { isEqual } from 'lodash-es';
import {
  ApplicationConfiguration,
  ApplicationDescriptor, ApplicationStartType,
  ApplicationType,
  CommandConfiguration,
  EndpointsConfiguration,
  ExecutableDescriptor,
  HttpEndpoint,
  InstanceConfiguration,
  LinkedValueConfiguration,
  OperatingSystem,
  ParameterConfiguration,
  ParameterConfigurationTarget,
  ParameterDescriptor,
  ProcessControlConfiguration,
  VariableType
} from 'src/app/models/gen.dtos';
import { getPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { VariableConfiguration } from './../../../../models/gen.dtos';

/** The type of a difference, typically propagated from simple attributes to complex diffs. */
export enum DiffType {
  NOT_IN_BASE = 'not-in-base',
  NOT_IN_COMPARE = 'not-in-compare',
  CHANGED = 'changed',
  UNCHANGED = 'unchanged',
}

/** Returns the DiffType for an object, comparing the two given instances in depth */
function getChangeType(base: unknown, compare: unknown): DiffType {
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
function getParentChangeType(base: unknown, compare: unknown, ...types: DiffType[]): DiffType {
  const noBase = base === null || base === undefined || (Array.isArray(base) && base.length === 0);
  const noCompare = compare === null || compare === undefined || (Array.isArray(compare) && compare.length === 0);
  if (noBase && !noCompare) {
    return DiffType.NOT_IN_BASE;
  } else if (noCompare && !noBase) {
    return DiffType.NOT_IN_COMPARE;
  }

  // changed if there is a single diff !== UNCHANGED.
  return types.every((e) => e === DiffType.UNCHANGED || e === undefined) ? DiffType.UNCHANGED : DiffType.CHANGED;
}

function getPreRenderableOrNull(lv: LinkedValueConfiguration): string {
  if (lv === null || lv === undefined) {
    return null;
  }
  return getPreRenderable(lv);
}

/** Maintains information about the difference in an object. The given value is the base value is set, otherwise the compare value. */
export class Difference<T> {
  /** The value to display to the user */
  public value: T;

  /** The type of difference to render */
  public type: DiffType;

  constructor(base: T, compare: T, valueOverride?: T) {
    this.value = valueOverride || (base ?? compare);
    this.type = getChangeType(base, compare);
  }
}

/** Differences in the ProcessControlConfiguration of an Application */
export class ProcessControlDiff {
  public type: DiffType;

  public startType: Difference<ApplicationStartType>;
  public keepAlive: Difference<boolean>;
  public noOfRetries: Difference<number>;
  public gracePeriod: Difference<number>;
  public attachStdin: Difference<boolean>;

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
      this.attachStdin.type,
    );
  }
}

/** Differences in one of the CommandConfigurations in an ApplicationConfiguration */
export class CommandDiff {
  public type: DiffType;
  public executable: Difference<string>;
  public parameters: ParameterDiff[] = [];
  public environment: ParameterDiff[] = [];

  constructor(base: CommandConfiguration, compare: CommandConfiguration, baseDescriptor: ExecutableDescriptor) {
    this.executable = new Difference(base?.executable, compare?.executable);

    if (!!base && !!base.parameters?.length) {
      for (const param of base.parameters) {
        const compareParam = compare?.parameters?.find((p) => p.id === param.id);
        (param.target === ParameterConfigurationTarget.ENVIRONMENT ? this.environment : this.parameters).push(
          new ParameterDiff(
            param,
            compareParam,
            baseDescriptor?.parameters?.find((p) => p.id === param.id),
          ),
        );
      }
    }
    let newParamChange: DiffType = DiffType.UNCHANGED;
    if (!!compare && !!compare.parameters?.length) {
      for (const param of compare.parameters) {
        const baseParam = base?.parameters?.find((p) => p.id === param.id);
        if (!baseParam) {
          newParamChange = DiffType.CHANGED;
        }
      }
    }

    this.type = getParentChangeType(
      base,
      compare,
      this.executable.type,
      newParamChange,
      ...this.parameters.map((p) => p.type),
      ...this.environment.map((p) => p.type),
    );
  }
}

/** Differences in a single parameter of a CommandConfiguration. Creates diffs for every pre-rendered part of the parameter if possible */
export class ParameterDiff {
  public type: DiffType;
  public values: Difference<string>[] = [];

  constructor(
    base: ParameterConfiguration,
    compare: ParameterConfiguration,
    public descriptor: ParameterDescriptor,
  ) {
    // in case this is an environment variable, we can shorten things.
    if (descriptor?.type === VariableType.ENVIRONMENT) {
      this.values.push(
        new Difference(
          base?.value ? `${descriptor.parameter}=${getPreRenderable(base.value)}` : null,
          compare?.value ? `${descriptor.parameter}=${getPreRenderable(compare.value)}` : null,
        ),
      );
      this.type = getParentChangeType(base, compare, ...this.values.map((d) => d.type));
      return;
    }

    // in case we KNOW this is a PASSWORD parameter, we want to pre-mask the value in each actual value.
    const maskingLV =
      descriptor?.type === VariableType.PASSWORD
        ? base?.value === null || base?.value === undefined
          ? compare?.value
          : base.value
        : null;

    const maskingValue = getPreRenderable(maskingLV);

    if (base?.preRendered?.length) {
      if (base.preRendered.length !== compare?.preRendered?.length) {
        // DIFFERENT length, we cannot *directly* compare the values.
        for (const val of base.preRendered) {
          const masked = maskingValue ? val.replace(maskingValue, '*'.repeat(maskingValue.length)) : val;
          this.values.push(new Difference(val, getPreRenderableOrNull(compare?.value), masked));
        }
      } else {
        for (let i = 0; i < base.preRendered.length; ++i) {
          const masked = maskingValue
            ? base.preRendered[i].replace(maskingValue, '*'.repeat(maskingValue.length))
            : base.preRendered[i];
          this.values.push(new Difference(base.preRendered[i], compare.preRendered[i], masked));
        }
      }
    } else if (compare?.preRendered?.length) {
      // we don't have anything, but they got some.
      for (const compareVal of compare.preRendered) {
        const masked = maskingValue ? compareVal.replace(maskingValue, '*'.repeat(maskingValue.length)) : compareVal;
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

  public path: Difference<string>;
  public port: Difference<string>;
  public secure: Difference<string>;
  public trustAll: Difference<boolean>;
  public trustStore: Difference<string>;
  public trustStorePass: Difference<string>;
  public authType: Difference<string>;
  public authUser: Difference<string>;
  public authPass: Difference<string>;

  constructor(base: HttpEndpoint, compare: HttpEndpoint) {
    this.path = new Difference(base?.path, compare?.path);
    this.port = new Difference(getPreRenderableOrNull(base?.port), getPreRenderableOrNull(compare?.port));
    this.secure = new Difference(getPreRenderableOrNull(base?.secure), getPreRenderableOrNull(compare?.secure));
    this.trustAll = new Difference(base?.trustAll, compare?.trustAll);
    this.trustStore = new Difference(
      getPreRenderableOrNull(base?.trustStore),
      getPreRenderableOrNull(compare?.trustStore),
    );
    this.trustStorePass = new Difference(
      getPreRenderableOrNull(base?.trustStorePass),
      getPreRenderableOrNull(compare?.trustStorePass),
    );
    this.authType = new Difference(getPreRenderableOrNull(base?.authType), getPreRenderableOrNull(compare?.authType));
    this.authUser = new Difference(getPreRenderableOrNull(base?.authUser), getPreRenderableOrNull(compare?.authUser));
    this.authPass = new Difference(getPreRenderableOrNull(base?.authPass), getPreRenderableOrNull(compare?.authPass));

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
      this.authPass.type,
    );
  }
}

/** All differences between two ApplicationConfigurations */
export class ApplicationConfigurationDiff {
  public type: DiffType;

  public id: Difference<string>;
  public name: Difference<string>;
  public processControl: ProcessControlDiff;
  public start: CommandDiff;
  public endpoints: EndpointsDiff;
  public os: OperatingSystem;

  constructor(
    leftConfig: ApplicationConfiguration,
    rightConfig: ApplicationConfiguration,
    public descriptor: ApplicationDescriptor,
  ) {
    this.id = new Difference(leftConfig?.id, rightConfig?.id);
    this.name = new Difference(leftConfig?.name, rightConfig?.name);
    if (descriptor?.type !== ApplicationType.CLIENT) {
      this.processControl = new ProcessControlDiff(leftConfig?.processControl, rightConfig?.processControl);
    }
    this.start = new CommandDiff(leftConfig?.start, rightConfig?.start, descriptor?.startCommand);
    this.endpoints = new EndpointsDiff(leftConfig?.endpoints, rightConfig?.endpoints);
    this.type = getParentChangeType(
      leftConfig,
      rightConfig,
      this.name.type,
      this.processControl?.type,
      this.start.type,
      this.endpoints.type,
    );

    this.os =
      leftConfig === null || leftConfig === undefined
        ? getAppOs(rightConfig.application)
        : getAppOs(leftConfig.application);
  }
}

/** Differences in the configuration between two InstanceConfigurations */
export class InstanceConfigurationDiff {
  public type: DiffType;

  public name: Difference<string>;
  public description: Difference<string>;
  public autoStart: Difference<boolean>;
  public purpose: Difference<string>;
  public productTag: Difference<string>;
  public configTree: Difference<string>;
  public autoUninstall: Difference<boolean>;
  public system: Difference<string>;
  public systemTag: Difference<string>;

  constructor(base: InstanceConfiguration, compare: InstanceConfiguration) {
    this.name = new Difference(base?.name, compare?.name);
    this.description = new Difference(base?.description, compare?.description);
    this.autoStart = new Difference(base?.autoStart, compare?.autoStart);
    this.purpose = new Difference(base?.purpose, compare?.purpose);
    this.productTag = new Difference(base?.product?.tag, compare?.product?.tag);
    this.configTree = new Difference(base?.configTree?.id, compare?.configTree?.id);
    this.autoUninstall = new Difference(base?.autoUninstall, compare?.autoUninstall);
    this.system = new Difference(base?.system?.name, compare?.system?.name);
    this.systemTag = new Difference(base?.system?.tag, compare?.system?.tag);

    this.type = getParentChangeType(
      base,
      compare,
      this.name.type,
      this.description.type,
      this.autoStart.type,
      this.purpose.type,
      this.productTag.type,
      this.configTree.type,
      this.autoUninstall.type,
      this.system.type,
      this.systemTag.type,
    );
  }
}

export class VariableValueDiff {
  public diffType: DiffType;

  public value: Difference<string>;
  public description: Difference<string>;
  public type: Difference<string>;
  public customEditor: Difference<string>;

  constructor(
    public key: string,
    public base: VariableConfiguration,
    public compare: VariableConfiguration,
  ) {
    this.value = new Difference(
      getPreRenderableOrNull(base?.value),
      getPreRenderableOrNull(compare?.value),
      base?.type === VariableType.PASSWORD || compare?.type === VariableType.PASSWORD
        ? '*'.repeat(getPreRenderable(base.value)?.length)
        : null,
    );
    this.description = new Difference(base?.description, compare?.description);
    this.type = new Difference(base?.type || VariableType.STRING, compare?.type || VariableType.STRING); // default is STRING
    this.customEditor = new Difference(base?.customEditor, compare?.customEditor);

    this.diffType = getParentChangeType(
      base,
      compare,
      this.value.type,
      this.description.type,
      this.type.type,
      this.customEditor.type,
    );
  }
}

export class VariablesDiff {
  public type: DiffType;
  public diffs: VariableValueDiff[] = [];

  constructor(base: VariableConfiguration[], compare: VariableConfiguration[]) {
    // only look at base params.
    if (base?.length) {
      for (const v of base) {
        this.diffs.push(
          new VariableValueDiff(
            v.id,
            v,
            compare.find((x) => x.id === v.id),
          ),
        );
      }
    }

    // check for variables not in base, which means its new, which means we're changed.
    let newVarChange: DiffType = DiffType.UNCHANGED;
    if (compare?.length) {
      for (const v of compare) {
        if (!base?.find((x) => x.id === v.id)) {
          newVarChange = DiffType.CHANGED;
        }
      }
    }

    this.type = getParentChangeType(base, compare, newVarChange, ...this.diffs.map((d) => d.diffType));
  }
}

@Injectable({
  providedIn: 'root',
})
export class HistoryDiffService {
  public diffAppConfig(
    base: ApplicationConfiguration,
    compare: ApplicationConfiguration,
    baseDescriptor: ApplicationDescriptor,
  ): ApplicationConfigurationDiff {
    return new ApplicationConfigurationDiff(base, compare, baseDescriptor);
  }

  public diffInstanceConfig(base: InstanceConfiguration, compare: InstanceConfiguration): InstanceConfigurationDiff {
    return new InstanceConfigurationDiff(base, compare);
  }

  public diffInstanceVariables(base: InstanceConfiguration, compare: InstanceConfiguration): VariablesDiff {
    return new VariablesDiff(base.instanceVariables, compare.instanceVariables);
  }
}
