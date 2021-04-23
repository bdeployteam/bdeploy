import { Injectable } from '@angular/core';
import { isEqual } from 'lodash-es';
import {
  ApplicationConfiguration,
  ApplicationDescriptor,
  CommandConfiguration,
  EndpointsConfiguration,
  ExecutableDescriptor,
  HttpEndpoint,
  OperatingSystem,
  ParameterConfiguration,
  ParameterDescriptor,
  ProcessControlConfiguration,
} from 'src/app/models/gen.dtos';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';

export enum DiffType {
  NOT_IN_BASE = 'not-in-base',
  NOT_IN_COMPARE = 'not-in-compare',
  CHANGED = 'changed',
  UNCHANGED = 'unchanged',
}

function getChangeType(base: any, compare: any): DiffType {
  if (isEqual(base, compare)) {
    return DiffType.UNCHANGED;
  } else if (base === null || base === undefined) {
    return DiffType.NOT_IN_BASE;
  } else if (compare === null || compare === undefined) {
    return DiffType.NOT_IN_COMPARE;
  } else {
    return DiffType.CHANGED;
  }
}

function getParentChangeType(base: any, compare: any, ...types: DiffType[]): DiffType {
  if (base === null || base === undefined) {
    return DiffType.NOT_IN_BASE;
  } else if (compare === null || compare === undefined) {
    return DiffType.NOT_IN_COMPARE;
  }

  // changed if there is a single diff !== UNCHANGED.
  return types.every((e) => e === DiffType.UNCHANGED) ? DiffType.UNCHANGED : DiffType.CHANGED;
}

export class Difference {
  /** The value to display to the user */
  public value: any;

  /** The type of difference to render */
  public type: DiffType;

  constructor(private base: any, private compare: any) {
    this.value = base === null || base === undefined ? compare : base;
    this.type = getChangeType(base, compare);
  }
}

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

export class ParameterDiff {
  public type: DiffType;
  public values: Difference[] = [];

  constructor(base: ParameterConfiguration, compare: ParameterConfiguration, public descriptor: ParameterDescriptor) {
    if (!!base?.preRendered?.length) {
      if (base.preRendered.length !== compare?.preRendered?.length) {
        // DIFFERENT length, we cannot *directly* compare the values.
        for (const val of base.preRendered) {
          this.values.push(new Difference(val, compare?.value));
        }
      } else {
        for (let i = 0; i < base.preRendered.length; ++i) {
          this.values.push(new Difference(base.preRendered[i], compare.preRendered[i]));
        }
      }
    } else if (!!compare?.preRendered?.length) {
      // we don't have anything, but they got some.
      for (const compareVal of compare.preRendered) {
        this.values.push(new Difference(null, compareVal));
      }
    }

    this.type = getParentChangeType(base, compare, ...this.values.map((d) => d.type));
  }
}

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

export class ApplicationConfigurationDiff {
  public type: DiffType;

  public name: Difference;
  public processControl: ProcessControlDiff;
  public start: CommandDiff;
  public endpoints: EndpointsDiff;
  public os: OperatingSystem;

  constructor(base: ApplicationConfiguration, compare: ApplicationConfiguration, public descriptor: ApplicationDescriptor) {
    this.name = new Difference(base?.name, compare?.name);
    this.processControl = new ProcessControlDiff(base?.processControl, compare?.processControl);
    this.start = new CommandDiff(base?.start, compare?.start, descriptor?.startCommand);
    this.endpoints = new EndpointsDiff(base?.endpoints, compare?.endpoints);
    this.type = getParentChangeType(base, compare, this.name.type, this.processControl.type, this.start.type);

    this.os = base === null || base === undefined ? getAppOs(compare.application) : getAppOs(base.application);
  }
}

@Injectable({
  providedIn: 'root',
})
export class HistoryDiffService {
  constructor() {}

  public diff(base: ApplicationConfiguration, compare: ApplicationConfiguration, baseDescriptor: ApplicationDescriptor): ApplicationConfigurationDiff {
    return new ApplicationConfigurationDiff(base, compare, baseDescriptor);
  }
}
