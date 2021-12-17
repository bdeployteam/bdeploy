import { Injectable } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { first, map, skipWhile, tap } from 'rxjs/operators';
import { StatusMessage } from 'src/app/models/config.model';
import {
  ApplicationConfiguration,
  ApplicationDto,
  CommandConfiguration,
  ExecutableDescriptor,
  InstanceNodeConfigurationDto,
  ParameterConditionType,
  ParameterConfiguration,
  ParameterDescriptor,
  ParameterType,
  ProcessControlConfiguration,
  ProductDto,
  TemplateApplication,
  TemplateParameter,
} from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessEditService {
  public node$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  public product$ = new BehaviorSubject<ProductDto>(null);
  public applications$ = new BehaviorSubject<ApplicationDto[]>(null);
  public process$ = new BehaviorSubject<ApplicationConfiguration>(null);
  public application$ = new BehaviorSubject<ApplicationDto>(null);

  private preliminary: ApplicationConfiguration[] = [];

  constructor(private edit: InstanceEditService, private products: ProductsService, private areas: NavAreasService, private groups: GroupsService) {
    combineLatest([this.areas.panelRoute$, this.products.products$, this.edit.state$, this.edit.stateApplications$]).subscribe(
      ([route, prods, state, apps]) => {
        const nodeName = route?.params['node'];
        const process = route?.params['process'];

        if (!nodeName || !prods || !state || !apps) {
          this.node$.next(null);
          this.product$.next(null);
          this.applications$.next(null);
          this.process$.next(null);
          return;
        }

        this.node$.next(state.config.nodeDtos.find((n) => n.nodeName === nodeName));
        this.product$.next(prods.find((p) => p.key.name === state.config.config.product.name && p.key.tag === state.config.config.product.tag));
        this.applications$.next(apps);

        if (!!process && !!this.node$.value?.nodeConfiguration?.applications) {
          this.process$.next(this.node$.value.nodeConfiguration.applications.find((a) => a.uid === process));
          this.application$.next(apps.find((x) => x.key.name === this.process$.value?.application?.name));
        }
      }
    );
  }

  public getApplication(key: string): Observable<ApplicationDto> {
    return this.applications$.pipe(
      skipWhile((a) => !a),
      map((a) => a.find((x) => x.key.name === key)),
      first()
    );
  }

  public removeProcess() {
    if (!this.node$.value || !this.process$.value) {
      return;
    }

    const apps = this.node$.value.nodeConfiguration.applications;
    apps.splice(
      apps.findIndex((a) => a.uid === this.process$.value.uid),
      1
    );
  }

  public addProcess(
    node: InstanceNodeConfigurationDto,
    application: ApplicationDto,
    template: TemplateApplication,
    variableValues: { [key: string]: string },
    status: StatusMessage[]
  ): Observable<string> {
    const start: CommandConfiguration = this.calculateInitialCommand(
      application.descriptor.startCommand,
      !!template ? template.startParameters : [],
      variableValues,
      status
    );
    const stop: CommandConfiguration = this.calculateInitialCommand(application.descriptor.stopCommand, [], {}, status);

    const process: ApplicationConfiguration = {
      uid: null, // calculated later
      application: application.key,
      name: !!template?.name ? this.performVariableSubst(template.name, variableValues, status) : application.name,
      pooling: application.descriptor.pooling,
      endpoints: cloneDeep(application.descriptor.endpoints),
      processControl: {
        startType: application.descriptor.processControl.supportedStartTypes[0],
        attachStdin: application.descriptor.processControl.attachStdin,
        gracePeriod: application.descriptor.processControl.gracePeriod,
        keepAlive: application.descriptor.processControl.supportsKeepAlive,
        noOfRetries: application.descriptor.processControl.noOfRetries,
      },
      start: start,
      stop: stop,
    };

    if (!!template?.processControl) {
      // partially deserialized - only apply specified attributes.
      const pc = template.processControl as ProcessControlConfiguration;
      if (pc.attachStdin !== undefined) {
        process.processControl.attachStdin = pc.attachStdin;
      }
      if (pc.gracePeriod !== undefined) {
        process.processControl.gracePeriod = pc.gracePeriod;
      }
      if (pc.keepAlive !== undefined) {
        process.processControl.keepAlive = pc.keepAlive;
      }
      if (pc.noOfRetries !== undefined) {
        process.processControl.noOfRetries = pc.noOfRetries;
      }
      if (pc.startType !== undefined) {
        process.processControl.startType = pc.startType;
      }
    }

    this.preliminary.push(process);
    this.alignGlobalParameters(application, process);

    return this.groups.newUuid().pipe(
      tap((uuid) => {
        process.uid = uuid;
        node.nodeConfiguration.applications.push(process);
        this.preliminary.splice(this.preliminary.indexOf(process), 1);
      })
    );
  }

  public preRenderParameter(desc: ParameterDescriptor, value: any): string[] {
    const strValue = !!value ? value : '';

    if (!desc) {
      // custom parameter;
      if (!strValue?.length) {
        return [];
      }
      return [strValue];
    }

    if (desc.hasValue) {
      if (desc.valueAsSeparateArg) {
        return [desc.parameter, strValue];
      }
      return [desc.parameter + desc.valueSeparator + strValue];
    } else if (desc.type === ParameterType.BOOLEAN && strValue === 'false') {
      return [];
    }
    return [desc.parameter];
  }

  public alignGlobalParameters(appDto: ApplicationDto, process: ApplicationConfiguration) {
    const globals = appDto.descriptor?.startCommand?.parameters?.filter((p) => p.global);
    if (!globals?.length) {
      return;
    }

    const values: { [key: string]: string } = {};
    for (const g of globals) {
      const v = process.start.parameters.find((p) => p.uid === g.uid);
      if (!!v) {
        values[v.uid] = v.value;
      }
    }

    for (const node of this.edit.state$.value?.config.nodeDtos) {
      for (const app of [...node.nodeConfiguration.applications, ...this.preliminary]) {
        for (const uid of Object.keys(values)) {
          const p = app.start?.parameters?.find((x) => x.uid === uid);
          if (!!p) {
            p.value = values[uid];
            p.preRendered = this.preRenderParameter(
              globals.find((x) => x.uid === uid),
              p.value
            );
          }
        }
      }
    }
  }

  public getGlobalParameter(uid: string): ParameterConfiguration {
    for (const node of this.edit.state$.value?.config.nodeDtos) {
      for (const app of [...node.nodeConfiguration.applications, ...this.preliminary]) {
        const p = app.start?.parameters?.find((x) => x.uid === uid);
        if (!!p) {
          return p;
        }
      }
    }
  }

  private calculateInitialCommand(
    descriptor: ExecutableDescriptor,
    templates: TemplateParameter[],
    values: { [key: string]: string },
    status: StatusMessage[]
  ): CommandConfiguration {
    if (!descriptor) {
      return null;
    }

    const mandatoryParams: ParameterConfiguration[] = descriptor.parameters
      .map((p) => {
        const tpl = templates.find((t) => t.uid === p.uid);

        if (!p.mandatory && !tpl) {
          return null;
        }

        let val = p.defaultValue;
        if (!!tpl && tpl.value !== undefined && tpl.value !== null) {
          val = this.performVariableSubst(tpl.value, values, status);
        } else if (p.global) {
          const gp = this.getGlobalParameter(p.uid);
          if (!!gp) {
            val = gp.value;
          }
        }

        return {
          uid: p.uid,
          value: val,
          preRendered: this.preRenderParameter(p, val),
        };
      })
      .filter((p) => !!p);

    return {
      executable: descriptor.launcherPath,
      parameters: mandatoryParams.filter((p) =>
        this.internalMeetsCondition(
          descriptor.parameters.find((x) => x.uid === p.uid),
          descriptor.parameters,
          mandatoryParams
        )
      ),
    };
  }

  private performVariableSubst(value: string, variables: { [key: string]: string }, status: StatusMessage[]): string {
    if (!!value && value.indexOf('{{T:') !== -1) {
      let found = true;
      while (found) {
        const rex = new RegExp('{{T:([^}]*)}}').exec(value);
        if (rex) {
          value = value.replace(rex[0], this.expandVar(rex[1], variables, status));
        } else {
          found = false;
        }
      }
    }
    return value;
  }

  private expandVar(variable: string, variables: { [key: string]: string }, status: StatusMessage[]): string {
    let varName = variable;
    const colIndex = varName.indexOf(':');
    if (colIndex !== -1) {
      varName = varName.substr(0, colIndex);
    }
    const val = variables[varName];

    if (colIndex !== -1) {
      const op = variable.substr(colIndex + 1);
      const opNum = Number(op);
      const valNum = Number(val);

      if (Number.isNaN(opNum) || Number.isNaN(valNum)) {
        status.push({
          icon: 'error',
          message: `Invalid variable substitution for ${variable}: '${op}' or '${val}' is not a number.`,
        });
        return variable;
      }
      return (valNum + opNum).toString();
    }

    return val;
  }

  public meetsCondition(param: ParameterDescriptor): Observable<boolean> {
    return combineLatest([this.application$, this.process$]).pipe(
      skipWhile(([a, c]) => !a || !c),
      map(([app, cfg]) => {
        return this.internalMeetsCondition(param, app.descriptor.startCommand.parameters, cfg.start.parameters);
      }),
      first()
    );
  }

  private internalMeetsCondition(param: ParameterDescriptor, allDescriptors: ParameterDescriptor[], allConfigs: ParameterConfiguration[]): boolean {
    if (!param.condition || !param.condition.parameter) {
      return true; // no condition, all OK :)
    }

    const depDesc = allDescriptors.find((p) => p.uid === param.condition.parameter);
    const depCfg = allConfigs.find((p) => p.uid === param.condition.parameter);
    if (!depDesc || !this.internalMeetsCondition(depDesc, allDescriptors, allConfigs)) {
      return false; // parameter not found?!
    }

    if (!depCfg || !depCfg.value) {
      if (param.condition.must === ParameterConditionType.BE_EMPTY) {
        return true;
      }
      return false;
    }

    const value = depCfg.value;

    switch (param.condition.must) {
      case ParameterConditionType.EQUAL:
        return value === param.condition.value;
      case ParameterConditionType.CONTAIN:
        return value.indexOf(param.condition.value) !== -1;
      case ParameterConditionType.START_WITH:
        return value.startsWith(param.condition.value);
      case ParameterConditionType.END_WITH:
        return value.endsWith(param.condition.value);
      case ParameterConditionType.BE_EMPTY:
        if (depDesc.type === ParameterType.BOOLEAN) {
          return value.trim() === 'false';
        }
        return value.trim().length <= 0;
      case ParameterConditionType.BE_NON_EMPTY:
        if (depDesc.type === ParameterType.BOOLEAN) {
          return value.trim() === 'true';
        }
        return value.trim().length > 0;
    }
  }
}
