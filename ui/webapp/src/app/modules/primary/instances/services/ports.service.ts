import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import {
  ApplicationPortStatesDto,
  BulkPortStatesDto,
  CompositePortStateDto,
  InstanceDto,
  InstanceNodeConfigurationListDto,
  MappedInstanceProcessStatusDto,
  MinionNodeType,
  MinionStatusDto,
  NodeType,
  PortStateDto,
  SystemConfigurationDto,
  VariableType
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { NO_LOADING_BAR_CONTEXT } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { suppressGlobalErrorHandling } from 'src/app/modules/core/utils/server.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { SystemsService } from '../../systems/services/systems.service';
import { InstancesService } from './instances.service';
import { ProcessesService } from './processes.service';

@Injectable({
  providedIn: 'root'
})
export class PortsService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly groups = inject(GroupsService);
  private readonly instances = inject(InstancesService);
  private readonly processes = inject(ProcessesService);
  private readonly systems = inject(SystemsService);

  private readonly apiPath = (g: string) => `${this.cfg.config.api}/group/${g}/instance`;

  // key is appId; this also compiles process state information if available
  public activePortStates$ = new BehaviorSubject<Record<string, ApplicationPortStatesDto>>(null);

  constructor() {
    combineLatest([
      this.instances.active$,
      this.instances.activeNodeCfgs$,
      this.processes.processStates$,
      this.instances.activeNodeStates$,
      this.systems.systems$
    ]).subscribe(([instance, nodes, instanceProcessStates, nodeStates, systems]) => {
      if (
        !instance ||
        !nodes ||
        !instanceProcessStates ||
        !nodeStates ||
        (instance?.instanceConfiguration?.system && !systems?.length)
      ) {
        this.activePortStates$.next(null);
        return;
      }

      this.loadActivePorts(
        instance,
        nodes,
        nodeStates,
        instanceProcessStates,
        systems?.find((s) => s.key.name === instance?.instanceConfiguration?.system?.name)
      );
    });
  }

  private loadActivePorts(
    instance: InstanceDto,
    cfgs: InstanceNodeConfigurationListDto,
    nodeStates: Record<string, MinionStatusDto>,
    instanceProcessStates: MappedInstanceProcessStatusDto,
    system: SystemConfigurationDto
  ) {
    if (!instance || !cfgs) {
      this.activePortStates$.next(null);
      return;
    }


    const applicationPortStates: Record<string, ApplicationPortStatesDto> = {};

    // collect ports, and check their states.
    for (const node of cfgs.nodeConfigDtos) {
      if (node.nodeConfiguration.nodeType === NodeType.CLIENT) {
        // no ports on client node.
        continue;
      }

      // if nof in the list, the node may be no longer configured on the server
      if (!Object.keys(nodeStates).includes(node.nodeName) ||
        (nodeStates[node.nodeName].config.minionNodeType !== MinionNodeType.MULTI && nodeStates[node.nodeName]?.offline)) {
        continue; // offline, don't bother.
      }

      for (const app of node.nodeConfiguration.applications) {
        const appStateDto: ApplicationPortStatesDto = {
          nodeType: node.nodeConfiguration.nodeType,
          configuredNode: node.nodeName,
          appId: app.id,
          appName: app.name,
          portStates: []
        };

        for (const param of app.start.parameters) {
          const appDesc = cfgs.applications.find((a) => a.key.name === app.application.name)?.descriptor;
          const paramDesc = appDesc?.startCommand.parameters.find((p) => p.id === param.id);

          if (!paramDesc || paramDesc.type !== VariableType.SERVER_PORT) {
            continue;
          }

          appStateDto.portStates.push({
            paramId: paramDesc.id,
            paramName: paramDesc.name,
            port: Number(
              // TODO: avoid calling this all the time. provide batched version to only gather stuff once in linked-values.utils.ts
              getRenderPreview(
                param.value,
                app,
                {
                  config: instance.instanceConfiguration,
                  nodeDtos: cfgs.nodeConfigDtos
                },
                system?.config
              )
            ),
            states: []
          } as CompositePortStateDto);
        }

        if (appStateDto.portStates.length > 0) {
          applicationPortStates[appStateDto.appId] = appStateDto;
        }
      }
    }

    if (Object.keys(applicationPortStates).length > 0) {
      this.http.post<BulkPortStatesDto>(
        `${this.apiPath(this.groups.current$.value.name)}/${instance.instanceConfiguration.id}/check-ports-bulk`,
        Object.entries(applicationPortStates).reduce((map, [appId, appState]) => {
          const ports = appState.portStates.map(ps => ps.port);

          map[appState.configuredNode] = [
            ...(map[appState.configuredNode] ?? []),
            ...ports
          ];

          return map;
        }, {} as Record<string, number[]>),
        {
          headers: suppressGlobalErrorHandling(new HttpHeaders()),
          context: NO_LOADING_BAR_CONTEXT
        }
      )
        .pipe(measure(`Ports of ${instance.instanceConfiguration.id}`))
        .subscribe({
          next: (result) => {
            for (const appState of Object.values(applicationPortStates)) {
              appState.portStates.forEach(compositeState => {
                const mappedCheckResult = result.node2Ports[appState.configuredNode];
                if (mappedCheckResult) {
                  for (const [serverNode, checkResult] of Object.entries(mappedCheckResult)) {
                    const portState = {
                      serverNode: serverNode,
                      isUsed: checkResult[compositeState.port],
                      processState: instanceProcessStates.processStates[appState.appId]?.[serverNode]?.processState
                    } as PortStateDto;
                    compositeState.states.push(portState);
                  }
                }
              });
            }

            this.activePortStates$.next(applicationPortStates);
          },
          error: (err) => {
            this.activePortStates$.next({});
            this.instances.reloadActiveStates(this.instances.active$.value);
          }
        });
    } else {
      this.activePortStates$.next({});
    }
  }

  public static isOk(portState: PortStateDto): boolean {
    return (ProcessesService.isRunning(portState.processState) ? portState.isUsed : !portState.isUsed);
  }
}
