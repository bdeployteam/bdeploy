import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  ApplicationStartType,
  InstanceNodeConfigurationDto,
  MinionStatusDto,
  ProcessState,
  ProcessStatusDto,
} from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from '../../../services/instances.service';
import {
  NodeApplicationPort,
  PortsService,
} from '../../../services/ports.service';
import { ProcessesService } from '../../../services/processes.service';
import { StateItem, StateType } from '../state-panel/state-panel.component';

@Component({
  selector: 'app-instance-server-node',
  templateUrl: './server-node.component.html',
})
export class ServerNodeComponent implements OnInit, OnDestroy {
  @Input() node: InstanceNodeConfigurationDto;

  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<
    BdDataGrouping<ApplicationConfiguration>[]
  >;
  @Input() collapsedWhen$: BehaviorSubject<boolean>;
  @Input() narrowWhen$: BehaviorSubject<boolean>;

  /* template */ nodeState$ = new BehaviorSubject<MinionStatusDto>(null);
  /* template */ nodeStateItems$ = new BehaviorSubject<StateItem[]>([]);

  private subscription: Subscription;
  private portsState = new BehaviorSubject<StateType>('unknown');
  private portsTooltip = new BehaviorSubject<string>(
    'State of all server ports'
  );
  private portsItem: StateItem = {
    name: 'Server Ports',
    type: this.portsState,
    tooltip: this.portsTooltip,
  };

  private processesState = new BehaviorSubject<StateType>('unknown');
  private processesTooltip = new BehaviorSubject<string>(
    'State of all server processes'
  );
  private processesItem: StateItem = {
    name: 'Instance Processes',
    type: this.processesState,
    tooltip: this.processesTooltip,
  };

  constructor(
    private instances: InstancesService,
    private ports: PortsService,
    public processes: ProcessesService,
    private auth: AuthenticationService,
    private areas: NavAreasService
  ) {}

  ngOnInit(): void {
    this.subscription = this.instances.activeNodeStates$.subscribe((states) => {
      if (!states || !states[this.node.nodeName]) {
        this.nodeState$.next(null);
        this.nodeStateItems$.next([]);
        return;
      }
      const state = states[this.node.nodeName];
      this.nodeState$.next(state);

      const updAvail = !!this.instances.current$.value?.newerVersionAvailable;

      const items: StateItem[] = [];
      items.push({
        name: this.node.nodeConfiguration.product.tag,
        type: updAvail ? 'update' : 'product',
        tooltip: `Product Version: ${this.node.nodeConfiguration.product.tag}${
          updAvail ? ' - Newer version available' : ''
        }`,
        click: this.auth.isCurrentScopeWrite()
          ? () => {
              this.areas.navigateBoth(
                [
                  'instances',
                  'configuration',
                  this.areas.groupContext$.value,
                  this.node.nodeConfiguration.uuid,
                ],
                ['panels', 'instances', 'settings', 'product']
              );
            }
          : null,
      });
      items.push({
        name: state.offline ? 'Offline' : 'Online',
        type: state.offline ? 'warning' : 'ok',
      });
      items.push(this.processesItem);
      items.push(this.portsItem);

      this.nodeStateItems$.next(items);
    });

    this.subscription.add(
      combineLatest([
        this.ports.activePortStates$,
        this.processes.processStates$,
      ]).subscribe(([ports, states]) => {
        this.updateAllProcesses(states);
        this.updateAllPortsRating(ports, states);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private updateAllProcesses(states: { [key: string]: ProcessStatusDto }) {
    if (!states || Object.keys(states).length === 0) {
      this.processesState.next('unknown');
      this.processesTooltip.next('No information available');
      return;
    }

    let runningApps = 0;
    let stoppedApps = 0;
    let deadApps = 0;
    this.node.nodeConfiguration.applications.forEach((app) => {
      if (app.processControl.startType === ApplicationStartType.INSTANCE) {
        const state = ProcessesService.get(states, app.uid)?.processState;
        if (!ProcessesService.isRunning(state)) {
          stoppedApps++;
        } else {
          if (state === ProcessState.RUNNING_NOT_ALIVE) {
            deadApps++;
          } else {
            runningApps++;
          }
        }
      }
    });

    this.processesState.next(
      !stoppedApps && !deadApps ? 'ok' : !runningApps ? 'info' : 'warning'
    );
    this.processesTooltip.next(
      !runningApps
        ? 'The instance is stopped'
        : !stoppedApps && !deadApps
        ? 'All applications OK'
        : `${stoppedApps} 'Instance' type ${
            stoppedApps === 1 ? 'application is' : 'applications are'
          } not running.\n${deadApps} 'Instance' type ${
            deadApps === 1
              ? 'application reports'
              : 'applications are reporting'
          } problems.`
    );
  }

  private updateAllPortsRating(
    ports: NodeApplicationPort[],
    states: { [key: string]: ProcessStatusDto }
  ) {
    if (!ports || !states) {
      this.portsState.next('unknown');
      this.portsTooltip.next('No information available');
      return;
    }

    const appPorts = ports.filter(
      (p) =>
        !!this.node.nodeConfiguration.applications.find(
          (a) => a.uid === p.appUid
        )
    );

    let badPorts = 0;
    appPorts.forEach((p) => {
      const process = ProcessesService.get(states, p.appUid);
      if (ProcessesService.isRunning(process.processState) !== p.state) {
        badPorts++;
      }
    });

    this.portsState.next(!badPorts ? 'ok' : 'warning');
    this.portsTooltip.next(
      !badPorts
        ? `All ${appPorts.length} server ports OK`
        : `${badPorts} of ${appPorts.length} server ports are rated bad.`
    );
  }
}
