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
  LinkedValueConfiguration,
  ParameterConditionType,
  ParameterConfiguration,
  ParameterDescriptor,
  ParameterType,
  ProcessControlConfiguration,
  ProcessControlGroupConfiguration,
  ProductDto,
  TemplateApplication,
  TemplateParameter,
} from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  createLinkedValue,
  getPreRenderable,
  getRenderPreview,
} from 'src/app/modules/core/utils/linked-values.utils';
import { expandVar } from 'src/app/modules/core/utils/object.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

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

  constructor(
    private edit: InstanceEditService,
    private products: ProductsService,
    private areas: NavAreasService,
    private groups: GroupsService,
    private systems: SystemsService
  ) {
    combineLatest([
      this.areas.panelRoute$,
      this.products.products$,
      this.edit.state$,
      this.edit.stateApplications$,
    ]).subscribe(([route, prods, state, apps]) => {
      const nodeName = route?.params['node'];
      const process = route?.params['process'];

      if (!nodeName || !prods || !state || !apps) {
        this.node$.next(null);
        this.product$.next(null);
        this.applications$.next(null);
        this.process$.next(null);
        return;
      }

      this.node$.next(
        state.config.nodeDtos.find((n) => n.nodeName === nodeName)
      );
      this.product$.next(
        prods.find(
          (p) =>
            p.key.name === state.config.config.product.name &&
            p.key.tag === state.config.config.product.tag
        )
      );
      this.applications$.next(apps);

      if (!!process && !!this.node$.value?.nodeConfiguration?.applications) {
        this.process$.next(
          this.node$.value.nodeConfiguration.applications.find(
            (a) => a.id === process
          )
        );
        this.application$.next(
          apps.find(
            (x) => x.key.name === this.process$.value?.application?.name
          )
        );
      }
    });
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
      apps.findIndex((a) => a.id === this.process$.value.id),
      1
    );

    // remove from control group(s)
    for (const grp of this.node$.value.nodeConfiguration.controlGroups) {
      const idx = grp.processOrder.findIndex(
        (id) => id === this.process$.value.id
      );
      if (idx !== -1) {
        grp.processOrder.splice(idx, 1);
      }
    }
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
      template ? template.startParameters : [],
      variableValues,
      status
    );
    const stop: CommandConfiguration = this.calculateInitialCommand(
      application.descriptor.stopCommand,
      [],
      {},
      status
    );

    const process: ApplicationConfiguration = {
      id: null, // calculated later
      uid: null, // compat
      application: application.key,
      name: template?.name
        ? this.performTemplateVariableSubst(
            template.name,
            variableValues,
            status
          )
        : application.name,
      pooling: application.descriptor.pooling,
      endpoints: cloneDeep(application.descriptor.endpoints),
      processControl: {
        startType: application.descriptor.processControl.supportedStartTypes[0],
        attachStdin: application.descriptor.processControl.attachStdin,
        gracePeriod: application.descriptor.processControl.gracePeriod,
        keepAlive: application.descriptor.processControl.supportsKeepAlive,
        noOfRetries: application.descriptor.processControl.noOfRetries,
        startupProbe: application.descriptor.processControl.startupProbe,
        lifenessProbe: application.descriptor.processControl.lifenessProbe,
        configDirs: application.descriptor.processControl.configDirs,
      },
      start: start,
      stop: stop,
    };

    if (template?.processControl) {
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

    // align global parameters *OR* migrate globals to instance variables.
    this.alignGlobalParameters(application, process, true);

    return this.groups.newId().pipe(
      tap((id) => {
        process.id = id;
        process.uid = id; // compat
        node.nodeConfiguration.applications.push(process);

        const reqGrp = template?.preferredProcessControlGroup;
        let targetGroup: ProcessControlGroupConfiguration;
        if (reqGrp) {
          targetGroup = node.nodeConfiguration.controlGroups.find(
            (g) => g.name === reqGrp
          );
        }
        if (!targetGroup) {
          targetGroup = this.edit.getLastControlGroup(node.nodeConfiguration);
        }

        targetGroup.processOrder.push(id);
        this.preliminary.splice(this.preliminary.indexOf(process), 1);
      })
    );
  }

  public preRenderParameter(
    desc: ParameterDescriptor,
    value: LinkedValueConfiguration
  ): string[] {
    const lvv = getPreRenderable(value);
    const strValue = lvv === null || lvv === undefined ? '' : lvv;

    if (!desc) {
      // custom parameter;
      if (!strValue?.length) {
        return [];
      }
      return [strValue];
    }

    if (desc.hasValue) {
      if (!desc.parameter) {
        return [strValue];
      }
      if (desc.valueAsSeparateArg) {
        return [desc.parameter, strValue];
      }
      return [desc.parameter + desc.valueSeparator + strValue];
    } else if (desc.type === ParameterType.BOOLEAN && strValue === 'false') {
      return [];
    }
    return [desc.parameter];
  }

  /**
   * Applies each global parameter *from* the given application to all other applications which
   * refer to the same global parameter.
   */
  public alignGlobalParameters(
    appDto: ApplicationDto,
    process: ApplicationConfiguration,
    migrate: boolean
  ) {
    const globals = appDto.descriptor?.startCommand?.parameters?.filter(
      (p) => p.global
    );
    if (!globals?.length) {
      return;
    }

    if (this.edit.globalsMigrated$.value) {
      if (migrate) {
        for (const g of globals) {
          const v = process.start.parameters.find((p) => p.id === g.id);
          if (v) {
            this.edit.migrateGlobalToVariable(
              this.edit.state$.value.config.config,
              v,
              g
            );
          }
        }
      }
      return; // skip the rest, as legacy globals are no longer supported.
    }

    const values: { [key: string]: LinkedValueConfiguration } = {};
    for (const g of globals) {
      const v = process.start.parameters.find((p) => p.id === g.id);
      if (v) {
        values[v.id] = v.value;
      }
    }

    // eslint-disable-next-line no-unsafe-optional-chaining
    for (const node of this.edit.state$.value?.config.nodeDtos) {
      for (const app of [
        ...node.nodeConfiguration.applications,
        ...this.preliminary,
      ]) {
        for (const id of Object.keys(values)) {
          const p = app.start?.parameters?.find((x) => x.id === id);
          if (p) {
            p.value = values[id];
            p.preRendered = this.preRenderParameter(
              globals.find((x) => x.id === id),
              p.value
            );
          }
        }
      }
    }
  }

  public getGlobalParameter(id: string): ParameterConfiguration {
    // eslint-disable-next-line no-unsafe-optional-chaining
    for (const node of this.edit.state$.value?.config.nodeDtos) {
      for (const app of [
        ...node.nodeConfiguration.applications,
        ...this.preliminary,
      ]) {
        const p = app.start?.parameters?.find((x) => x.id === id);
        if (p) {
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
        const tpl = templates.find((t) => t.id === p.id);

        if (!p.mandatory && !tpl) {
          return null;
        }

        let val = p.defaultValue;
        if (!!tpl && tpl.value !== undefined && tpl.value !== null) {
          val = createLinkedValue(
            this.performTemplateVariableSubst(tpl.value, values, status)
          );
        } else if (p.global && !this.edit.globalsMigrated$.value) {
          const gp = this.getGlobalParameter(p.id);
          if (gp) {
            val = gp.value;
          }
        }

        return {
          id: p.id,
          uid: p.id, // compat
          value: val,
          pinned: false,
          preRendered: this.preRenderParameter(p, val),
        };
      })
      .filter((p) => !!p);

    return {
      executable: descriptor.launcherPath,
      parameters: mandatoryParams.filter((p) =>
        this.meetsConditionOnGiven(
          descriptor.parameters.find((x) => x.id === p.id),
          descriptor,
          {
            // dummy just so we can resolve from our own parameters during adding.
            // this may be problematic in case of "condition chains".
            start: {
              executable: descriptor.launcherPath,
              parameters: mandatoryParams,
            },
          } as ApplicationConfiguration
        )
      ),
    };
  }

  public performTemplateVariableSubst(
    value: string,
    variables: { [key: string]: string },
    status: StatusMessage[]
  ): string {
    if (!!value && value.indexOf('{{T:') !== -1) {
      let found = true;
      while (found) {
        const rex = new RegExp(/{{T:([^}]*)}}/).exec(value);
        if (rex) {
          value = value.replace(rex[0], expandVar(rex[1], variables, status));
        } else {
          found = false;
        }
      }
    }
    return value;
  }

  public meetsConditionOnCurrent(
    param: ParameterDescriptor
  ): Observable<boolean> {
    return combineLatest([this.application$, this.process$]).pipe(
      skipWhile(([a, c]) => !a || !c),
      map(([app, cfg]) => {
        return this.meetsConditionOnGiven(
          param,
          app.descriptor.startCommand,
          cfg
        );
      }),
      first()
    );
  }

  private meetsConditionOnGiven(
    param: ParameterDescriptor,
    descriptor: ExecutableDescriptor,
    process: ApplicationConfiguration
  ): boolean {
    if (
      !param.condition ||
      (!param.condition.parameter && !param.condition.expression)
    ) {
      return true; // no condition, all OK :)
    }

    let targetType = param.type;
    let expression = param.condition.expression;
    if (param.condition.parameter) {
      expression = `{{V:${param.condition.parameter}}}`;
      targetType =
        descriptor.parameters.find((p) => p.id === param.condition.parameter)
          ?.type || param.type;
    }

    const system =
      this.edit.state$.value?.config?.config?.system &&
      this.systems.systems$.value?.length
        ? this.systems.systems$.value.find(
            (s) =>
              s.key.name === this.edit.state$.value.config.config.system.name
          )
        : null;

    const value = getRenderPreview(
      createLinkedValue(expression),
      process,
      this.edit.state$.value?.config,
      system?.config
    );

    // no value or value could not be resolved fully.
    if (value === null || value === undefined || value.indexOf('{{') !== -1) {
      return param.condition.must === ParameterConditionType.BE_EMPTY;
    }

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
        return (
          value.trim().length <= 0 ||
          (targetType === ParameterType.BOOLEAN && value.trim() === 'false')
        );
      case ParameterConditionType.BE_NON_EMPTY:
        return (
          value.trim().length > 0 &&
          !(targetType === ParameterType.BOOLEAN && value.trim() === 'false')
        );
    }
  }
}
