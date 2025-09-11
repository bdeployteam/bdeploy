import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest, forkJoin } from 'rxjs';
import {
  InstanceDto,
  InstanceNodeConfigurationListDto,
  MinionStatusDto,
  NodeType,
  SystemConfigurationDto,
  VariableType,
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

export interface NodeApplicationPort {
  node: string;
  appId: string;
  appName: string;
  paramId: string;
  paramName: string;
  port: number;
  state: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class PortsService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly groups = inject(GroupsService);
  private readonly instances = inject(InstancesService);
  private readonly processes = inject(ProcessesService);
  private readonly systems = inject(SystemsService);

  private readonly apiPath = (g: string) => `${this.cfg.config.api}/group/${g}/instance`;

  public activePortStates$ = new BehaviorSubject<NodeApplicationPort[]>(null);

  constructor() {
    combineLatest([
      this.instances.active$,
      this.instances.activeNodeCfgs$,
      this.processes.processStates$,
      this.instances.activeNodeStates$,
      this.systems.systems$,
    ]).subscribe(([instance, nodes, states, nodeStates, systems]) => {
      if (
        !instance ||
        !nodes ||
        !states ||
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
        systems?.find((s) => s.key.name === instance?.instanceConfiguration?.system?.name),
      );
    });
  }

  private loadActivePorts(
    instance: InstanceDto,
    cfgs: InstanceNodeConfigurationListDto,
    nodeStates: Record<string, MinionStatusDto>,
    system: SystemConfigurationDto,
  ) {
    if (!instance || !cfgs) {
      this.activePortStates$.next(null);
      return;
    }

    const allQueries: Observable<NodeApplicationPort[]>[] = [];

    // collect ports, and check their states.
    for (const node of cfgs.nodeConfigDtos) {
      if (node.nodeConfiguration.nodeType === NodeType.CLIENT) {
        // no ports on client node.
        continue;
      }

      // if nof in the list, the node may be no longer configured on the server
      if (!Object.keys(nodeStates).includes(node.nodeName) || nodeStates[node.nodeName]?.offline) {
        continue; // offline, don't bother.
      }

      const portsOfNode: NodeApplicationPort[] = [];

      for (const app of node.nodeConfiguration.applications) {
        for (const param of app.start.parameters) {
          const appDesc = cfgs.applications.find((a) => a.key.name === app.application.name)?.descriptor;
          const paramDesc = appDesc?.startCommand.parameters.find((p) => p.id === param.id);

          if (!paramDesc || paramDesc.type !== VariableType.SERVER_PORT) {
            continue;
          }

          portsOfNode.push({
            node: node.nodeName,
            appId: app.id,
            appName: app.name,
            paramId: param.id,
            paramName: paramDesc.name,
            port: Number(
              // TODO: avoid calling this all the time. provide batched version to only gather stuff once in linked-values.utils.ts
              getRenderPreview(
                param.value,
                app,
                {
                  config: instance.instanceConfiguration,
                  nodeDtos: cfgs.nodeConfigDtos,
                },
                system?.config,
              ),
            ),
            state: false,
          });
        }
      }

      if (portsOfNode.length > 0) {
        allQueries.push(
          new Observable((s) => {
            this.http
              .post<Record<number, boolean>>(
                `${this.apiPath(this.groups.current$.value.name)}/${instance.instanceConfiguration.id}/check-ports/${
                  node.nodeName
                }`,
                portsOfNode.map((na) => na.port),
                {
                  headers: suppressGlobalErrorHandling(new HttpHeaders()),
                  context: NO_LOADING_BAR_CONTEXT,
                },
              )
              .pipe(measure(`Ports of ${instance.instanceConfiguration.id}/${node.nodeName}`))
              .subscribe({
                next: (result) => {
                  for (const desc of portsOfNode) {
                    desc.state = result[desc.port];
                  }
                  s.next(portsOfNode);
                  s.complete();
                },
                error: (err) => {
                  this.instances.reloadActiveStates(this.instances.active$.value);
                  s.error(err);
                  s.complete();
                },
              });
          }),
        );
      }
    }
    if (allQueries.length > 0) {
      forkJoin(allQueries).subscribe((results) => {
        const flat: NodeApplicationPort[] = [].concat(...results);
        this.activePortStates$.next(flat);
      });
    } else {
      this.activePortStates$.next([]);
    }
  }
}
