import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { first, map, skipWhile, tap } from 'rxjs/operators';
import { StatusMessage } from 'src/app/models/config.model';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import {
  ApplicationConfiguration,
  ApplicationDto,
  CommandConfiguration,
  ExecutableDescriptor,
  FlattenedApplicationTemplateConfiguration,
  InstanceNodeConfigurationDto,
  LinkedValueConfiguration,
  ParameterConditionType,
  ParameterConfiguration,
  ParameterConfigurationTarget,
  ParameterDescriptor,
  ParameterType,
  ProcessControlConfiguration,
  ProcessControlGroupConfiguration,
  ProductDto,
  TemplateParameter,
} from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { createLinkedValue, getPreRenderable, getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { getAppKeyName, getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { performTemplateVariableSubst } from 'src/app/modules/core/utils/object.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessEditService {
  private edit = inject(InstanceEditService);
  private products = inject(ProductsService);
  private areas = inject(NavAreasService);
  private groups = inject(GroupsService);
  private systems = inject(SystemsService);
  private snackbar = inject(MatSnackBar);

  public node$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  public product$ = new BehaviorSubject<ProductDto>(null);
  public applications$ = new BehaviorSubject<ApplicationDto[]>(null);
  public process$ = new BehaviorSubject<ApplicationConfiguration>(null);
  public application$ = new BehaviorSubject<ApplicationDto>(null);

  private preliminary: ApplicationConfiguration[] = [];

  constructor() {
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

      this.node$.next(state.config.nodeDtos.find((n) => n.nodeName === nodeName));
      this.product$.next(
        prods.find(
          (p) => p.key.name === state.config.config.product.name && p.key.tag === state.config.config.product.tag
        )
      );
      this.applications$.next(apps);

      if (!!process && !!this.node$.value?.nodeConfiguration?.applications) {
        this.process$.next(this.node$.value.nodeConfiguration.applications.find((a) => a.id === process));
        this.application$.next(apps.find((x) => x.key.name === this.process$.value?.application?.name));
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
      const idx = grp.processOrder.findIndex((id) => id === this.process$.value.id);
      if (idx !== -1) {
        grp.processOrder.splice(idx, 1);
      }
    }
  }

  public addProcessPaste(appConfig: ApplicationConfiguration) {
    // need to find the proper application (linux vs. windows).
    const state = this.edit.nodes$.value[this.node$.value.nodeName];
    const appOs = getAppOs(appConfig.application);
    const isServerNode = this.node$.value.nodeName !== CLIENT_NODE_NAME;
    if (isServerNode && appOs && state.os !== appOs) {
      // different OS with OS bound application - need to find a more suitable one :)
      const keyName = getAppKeyName(appConfig.application);
      const replacement = this.applications$.value?.find(
        (a) => getAppKeyName(a.key) === keyName && getAppOs(a.key) === state.os
      );

      if (!replacement) {
        // cannot find suitable application, cannot continue;
        this.snackbar.open('Cannot find suitable application while pasting.', 'DISMISS');
        return;
      }

      appConfig.application = replacement.key;
    }

    const app = this.applications$.value.find((a) => appConfig.application.name === a.key.name);

    // Generate unique identifier
    this.groups.newId().subscribe((id) => {
      appConfig.application.tag = this.product$.value.key.tag;
      appConfig.id = id;
      appConfig.uid = id; // compat

      // no need to update mandatory (etc.) parameters here. the normal validation
      // will trigger according errors which need to be manually fixed by the user.

      // Update parameters for pasted app to avoid overwriting existing values.
      // there is no need to align global parameters in other apps, since no global
      // should have a value different from the ones in the instances already after
      // this alignment code.
      const globals = app.descriptor.startCommand.parameters.filter((p) => p.global);
      for (const global of globals) {
        const existing = this.getGlobalParameter(global.id);
        const own = appConfig.start.parameters.find((p) => p.id === global.id);
        if (existing && own) {
          own.value = existing.value;
        }
      }

      const fixed = app.descriptor.startCommand.parameters.filter((p) => p.fixed);
      for (const f of fixed) {
        const own = appConfig.start.parameters.find((p) => p.id === f.id);
        if (own) {
          own.value = f.defaultValue;
        }
      }

      // always pre-render all parameters.
      for (const param of app.descriptor.startCommand.parameters) {
        const own = appConfig.start.parameters.find((p) => p.id === param.id);
        if (own) {
          own.preRendered = this.preRenderParameter(param, own.value);
          own.target =
            param.type === ParameterType.ENVIRONMENT
              ? ParameterConfigurationTarget.ENVIRONMENT
              : ParameterConfigurationTarget.COMMAND;
        }
      }

      // always fully re-calculate stop command.
      appConfig.stop = this.calculateInitialCommand(app.descriptor.stopCommand, [], {}, []);

      this.node$.value.nodeConfiguration.applications.push(appConfig);
      this.edit.getLastControlGroup(this.node$.value.nodeConfiguration).processOrder.push(appConfig.id);
      this.edit.conceal(`Paste ${appConfig.name}`);
    });
  }

  public addProcess(
    node: InstanceNodeConfigurationDto,
    application: ApplicationDto,
    template: FlattenedApplicationTemplateConfiguration,
    variableValues: { [key: string]: string },
    status: StatusMessage[]
  ): Observable<string> {
    const start: CommandConfiguration = this.calculateInitialCommand(
      application.descriptor.startCommand,
      template ? template.startParameters : [],
      variableValues,
      status
    );
    const stop: CommandConfiguration = this.calculateInitialCommand(application.descriptor.stopCommand, [], {}, status);

    const templateProcessName = !!template?.processName ? template.processName : template?.name;

    const process: ApplicationConfiguration = {
      id: null, // calculated later
      uid: null, // compat
      application: application.key,
      name: templateProcessName
        ? performTemplateVariableSubst(templateProcessName, variableValues, status)
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

    // align global parameters.
    this.alignGlobalParameters(application, process);

    return this.groups.newId().pipe(
      tap((id) => {
        process.id = id;
        process.uid = id; // compat
        node.nodeConfiguration.applications.push(process);

        const reqGrp = template?.preferredProcessControlGroup;
        let targetGroup: ProcessControlGroupConfiguration;
        if (reqGrp) {
          targetGroup = node.nodeConfiguration.controlGroups.find((g) => g.name === reqGrp);
        }
        if (!targetGroup) {
          targetGroup = this.edit.getLastControlGroup(node.nodeConfiguration);
        }

        targetGroup.processOrder.push(id);
        this.preliminary.splice(this.preliminary.indexOf(process), 1);
      })
    );
  }

  public preRenderParameter(desc: ParameterDescriptor, value: LinkedValueConfiguration): string[] {
    const lvv = getPreRenderable(value);
    const strValue = lvv === null || lvv === undefined ? '' : lvv;

    if (!desc) {
      // custom parameter;
      if (!strValue?.length) {
        return [];
      }
      return [strValue];
    }

    if (desc.type === ParameterType.ENVIRONMENT) {
      return [desc.parameter, strValue];
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
  public alignGlobalParameters(appDto: ApplicationDto, process: ApplicationConfiguration) {
    const globals = appDto.descriptor?.startCommand?.parameters?.filter((p) => p.global);
    if (!globals?.length) {
      return;
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
      for (const app of [...node.nodeConfiguration.applications, ...this.preliminary]) {
        for (const id of Object.keys(values)) {
          const p = app.start?.parameters?.find((x) => x.id === id);
          if (p) {
            const desc = globals.find((x) => x.id === id);
            p.value = values[id];
            p.preRendered = this.preRenderParameter(desc, p.value);
            p.target =
              desc.type === ParameterType.ENVIRONMENT
                ? ParameterConfigurationTarget.ENVIRONMENT
                : ParameterConfigurationTarget.COMMAND;
          }
        }
      }
    }
  }

  public getGlobalParameter(id: string): ParameterConfiguration {
    // eslint-disable-next-line no-unsafe-optional-chaining
    for (const node of this.edit.state$.value?.config.nodeDtos) {
      for (const app of [...node.nodeConfiguration.applications, ...this.preliminary]) {
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
          val = createLinkedValue(performTemplateVariableSubst(tpl.value, values, status));
        } else if (p.global) {
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
          target:
            p.type === ParameterType.ENVIRONMENT
              ? ParameterConfigurationTarget.ENVIRONMENT
              : ParameterConfigurationTarget.COMMAND,
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

  public meetsConditionOnCurrent(param: ParameterDescriptor): Observable<boolean> {
    return combineLatest([this.application$, this.process$]).pipe(
      skipWhile(([a, c]) => !a || !c),
      map(([app, cfg]) => {
        return this.meetsConditionOnGiven(param, app.descriptor.startCommand, cfg);
      }),
      first()
    );
  }

  public meetsConditionOnGiven(
    param: ParameterDescriptor,
    descriptor: ExecutableDescriptor,
    process: ApplicationConfiguration
  ): boolean {
    if (!param.condition || (!param.condition.parameter && !param.condition.expression)) {
      return true; // no condition, all OK :)
    }

    let targetType = param.type;
    let expression = param.condition.expression;
    if (param.condition.parameter) {
      expression = `{{V:${param.condition.parameter}}}`;
      targetType = descriptor.parameters.find((p) => p.id === param.condition.parameter)?.type || param.type;
    }

    const system =
      this.edit.state$.value?.config?.config?.system && this.systems.systems$.value?.length
        ? this.systems.systems$.value.find((s) => s.key.name === this.edit.state$.value.config.config.system.name)
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
        return value.trim().length <= 0 || (targetType === ParameterType.BOOLEAN && value.trim() === 'false');
      case ParameterConditionType.BE_NON_EMPTY:
        return value.trim().length > 0 && !(targetType === ParameterType.BOOLEAN && value.trim() === 'false');
    }
  }
}
