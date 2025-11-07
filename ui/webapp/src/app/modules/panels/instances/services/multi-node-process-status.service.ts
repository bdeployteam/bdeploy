import { inject, Injectable, NgZone, OnDestroy } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { ApplicationConfiguration, InstanceNodeConfigurationDto, ProcessState } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';
import { PortsService } from '../../../primary/instances/services/ports.service';

export interface MultiProcessStatusDto {
  total: number;
  totalRunning: number;
  statusMap: Record<ProcessState, number>;
  nrOfOutdatedApps: number;
  arePortStatesKnown: boolean;
  processesWithBrokenPorts: number;
}

@Injectable({
  providedIn: 'root'
})
export class MultiNodeProcessStatusService implements OnDestroy {
  private readonly instances = inject(InstancesService);
  private readonly processes = inject(ProcessesService);
  private readonly ports = inject(PortsService);
  private readonly areas = inject(NavAreasService);
  private readonly zone = inject(NgZone);

  public multiStatus$ = new BehaviorSubject<MultiProcessStatusDto>(null);
  public processConfig$ = new BehaviorSubject<ApplicationConfiguration>(null);

  private readonly subscription: Subscription;

  constructor() {
    this.subscription = combineLatest([
      this.areas.panelRoute$,
      this.processes.processStates$,
      this.ports.activePortStates$,
      this.instances.active$,
      this.instances.activeNodeCfgs$
    ]).subscribe(([route, states, portStates, instance, nodes]) => {
      const appId = route?.params['process'];
      // check that the multi node status panel is open
      if (!route.url.some(segment => segment.path == 'multi-node-process') || !appId || !instance || !nodes) {
        this.zone.run(() => {
          this.processConfig$.next(null);
          this.multiStatus$.next(null);
        });
        return;
      }

      // attempting to find configuration without instanceStatus
      // because there might not be any nodes attached
      let app: ApplicationConfiguration = null;
      let nodeCfg: InstanceNodeConfigurationDto = null;
      for (const config of nodes.nodeConfigDtos) {
        app = config.nodeConfiguration.applications.find(a => a?.id === appId);
        if (app) {
          nodeCfg = config;
          break;
        }
      }

      if(app == null || nodeCfg == null) {
        this.zone.run(() => {
          this.processConfig$.next(null);
          this.multiStatus$.next(null);
        });
        return;
      }

      // if we have found the config we want to show the empty status
      const multiStatus = {
        total: 0,
        totalRunning: 0,
        nrOfOutdatedApps: 0,
        arePortStatesKnown: !!portStates,
        processesWithBrokenPorts: 0,
        statusMap: {
          [ProcessState.STOPPED]: 0,
          [ProcessState.STOPPED_START_PLANNED]: 0,
          [ProcessState.RUNNING]: 0,
          [ProcessState.RUNNING_UNSTABLE]: 0,
          [ProcessState.RUNNING_NOT_STARTED]: 0,
          [ProcessState.RUNNING_NOT_ALIVE]: 0,
          [ProcessState.RUNNING_STOP_PLANNED]: 0,
          [ProcessState.CRASHED_PERMANENTLY]: 0,
          [ProcessState.CRASHED_WAITING]: 0
        }
      } as MultiProcessStatusDto;

      Object.entries(states.processStates[appId] ?? []).forEach(
        entry => {
          const serverNode = entry[0];
          const processStatus = entry[1];

          multiStatus.total++;
          // this will be used for enabling/disabling buttons
          multiStatus.totalRunning += ProcessesService.isRunning(processStatus.processState) ? 1 : 0;

          if (portStates) {
            const portNotOk = portStates[appId]?.portStates?.flatMap(portState => portState.states)
              .find(processPortState => processPortState.serverNode === serverNode && !PortsService.isOk(processPortState));
            if (portNotOk) {
              multiStatus.processesWithBrokenPorts++;
            }
          }

          multiStatus.statusMap[processStatus.processState] += 1;
          multiStatus.nrOfOutdatedApps += processStatus.instanceTag !== this.instances.active$.value?.instance?.tag ? 1 : 0;
        }
      );


      this.zone.run(() => {
        this.multiStatus$.next(multiStatus);
        this.processConfig$.next(app);
      });
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

}
