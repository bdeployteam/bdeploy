import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest, forkJoin, Observable } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { InstanceDto, InstanceNodeConfigurationListDto, ParameterType } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NO_LOADING_BAR } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';
import { ProcessesService } from './processes.service';

export interface NodeApplicationPort {
  node: string;
  appUid: string;
  appName: string;
  paramUid: string;
  paramName: string;
  port: number;
  state: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class PortsService {
  activePortStates$ = new BehaviorSubject<NodeApplicationPort[]>(null);

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService,
    private instances: InstancesService,
    private processes: ProcessesService
  ) {
    combineLatest([this.instances.active$, this.instances.activeNodeCfgs$, this.processes.processStates$]).subscribe(([instance, nodes, states]) => {
      if (!instance || !nodes || !states) {
        this.activePortStates$.next(null);
        return;
      }

      this.loadActivePorts(instance, nodes);
    });
  }

  private loadActivePorts(instance: InstanceDto, cfgs: InstanceNodeConfigurationListDto) {
    if (!instance || !cfgs) {
      this.activePortStates$.next(null);
      return;
    }

    const allQueries: Observable<NodeApplicationPort[]>[] = [];

    // collect ports, and check their states.
    for (const node of cfgs.nodeConfigDtos) {
      if (node.nodeName === CLIENT_NODE_NAME) {
        // no ports on client node.
        continue;
      }

      const portsOfNode: NodeApplicationPort[] = [];

      for (const app of node.nodeConfiguration.applications) {
        for (const param of app.start.parameters) {
          const appDesc = cfgs.applications[app.application.name];
          const paramDesc = appDesc.startCommand.parameters.find((p) => p.uid === param.uid);

          if (!paramDesc || paramDesc.type !== ParameterType.SERVER_PORT) {
            continue;
          }

          portsOfNode.push({
            node: node.nodeName,
            appUid: app.uid,
            appName: app.name,
            paramUid: param.uid,
            paramName: paramDesc.name,
            port: Number(param.value),
            state: false,
          });
        }
      }

      allQueries.push(
        new Observable((s) => {
          this.http
            .post<{ [key: number]: boolean }>(
              `${this.apiPath(this.groups.current$.value.name)}/${instance.instanceConfiguration.uuid}/check-ports/${node.nodeName}`,
              portsOfNode.map((na) => na.port),
              NO_LOADING_BAR
            )
            .pipe(measure(`Ports of ${instance.instanceConfiguration.uuid}/${node.nodeName}`))
            .subscribe(
              (result) => {
                for (const desc of portsOfNode) {
                  desc.state = result[desc.port];
                }
                s.next(portsOfNode);
                s.complete();
              },
              (err) => {
                s.error(err);
                s.complete();
              }
            );
        })
      );

      forkJoin(allQueries).subscribe((results) => {
        const flat: NodeApplicationPort[] = [].concat(...results);
        this.activePortStates$.next(flat);
      });
    }
  }
}
