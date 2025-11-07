import { Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  ApplicationPortStatesDto,
  ApplicationStartType,
  InstanceNodeConfigurationDto,
  MappedInstanceProcessStatusDto,
  MinionStatusDto,
  NodeSynchronizationStatus,
  ProcessState
} from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from '../../../services/instances.service';
import { PortsService } from '../../../services/ports.service';
import { ProcessesService } from '../../../services/processes.service';
import { NodeStatePanelComponent, StateItem, StateType } from '../state-panel/state-panel.component';
import { BdPanelButtonComponent } from '../../../../../core/components/bd-panel-button/bd-panel-button.component';
import { NodeHeaderComponent } from './header/header.component';
import { MatDivider } from '@angular/material/divider';
import { NodeProcessListComponent } from './process-list/process-list.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-instance-server-node',
  templateUrl: './server-node.component.html',
  styleUrls: ['./server-node.component.css'],
  imports: [BdPanelButtonComponent, NodeHeaderComponent, NodeStatePanelComponent, MatDivider, NodeProcessListComponent, AsyncPipe]
})
export class ServerNodeComponent implements OnInit, OnDestroy {
  private readonly instances = inject(InstancesService);
  private readonly ports = inject(PortsService);
  private readonly auth = inject(AuthenticationService);
  private readonly areas = inject(NavAreasService);
  protected readonly processes = inject(ProcessesService);

  @Input() node: InstanceNodeConfigurationDto;

  @Input() bulkMode: boolean;
  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;
  @Input() collapsedWhen$: BehaviorSubject<boolean>;
  @Input() narrowWhen$: BehaviorSubject<boolean>;

  protected nodeState$ = new BehaviorSubject<MinionStatusDto>(null);
  protected nodeStateItems$ = new BehaviorSubject<StateItem[]>([]);
  protected synchronizationCollapse$ = new BehaviorSubject<boolean>(false);
  protected collapsed$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  private readonly portsState = new BehaviorSubject<StateType>('unknown');
  private readonly portsTooltip = new BehaviorSubject<string>('State of all server ports');
  private readonly portsItem: StateItem = {
    name: 'Server Ports',
    type: this.portsState,
    tooltip: this.portsTooltip
  };

  private readonly processesState = new BehaviorSubject<StateType>('unknown');
  private readonly processesTooltip = new BehaviorSubject<string>('State of all server processes');
  private readonly processesItem: StateItem = {
    name: 'Instance Processes',
    type: this.processesState,
    tooltip: this.processesTooltip
  };

  ngOnInit(): void {
    this.subscription = combineLatest([this.instances.activeNodeStates$, this.instances.productUpdates$]).subscribe(
      ([states, updates]) => {
        if (!states?.[this.node.nodeName]) {
          this.nodeState$.next(null);
          this.nodeStateItems$.next([]);
          return;
        }
        const state = states[this.node.nodeName];
        this.nodeState$.next(state);

        const items: StateItem[] = [];

        const updAvail = updates?.newerVersionAvailable;
        const updAvailInRepo = updates?.newerVersionAvailableInRepository;
        const anyUpdAvail = updAvail || updAvailInRepo;

        items.push({
          name: this.node.nodeConfiguration.product.tag,
          type: anyUpdAvail ? 'update' : 'product',
          tooltip: `Product Version: ${this.node.nodeConfiguration.product.tag}${
            updAvail
              ? '\n\nNewer version available'
              : updAvailInRepo
                ? '\n\nNewer version available for import from software repository'
                : ''
          }`,
          click:
            anyUpdAvail && this.auth.isCurrentScopeWrite()
              ? () => {
                this.areas.navigateBoth(
                  ['instances', 'configuration', this.areas.groupContext$.value, this.node.nodeConfiguration.id],
                  ['panels', 'instances', 'settings', 'product']
                );
              }
              : null
        });

        items.push(this.getNodeStateItem(state));

        const syncStatus = state.nodeSynchronizationStatus;
        if (syncStatus === NodeSynchronizationStatus.SYNCHRONIZED || syncStatus === NodeSynchronizationStatus.UNKNOWN) {
          items.push(this.processesItem);
          items.push(this.portsItem);
        }

        this.nodeStateItems$.next(items);

        const syncCollapse = [
          NodeSynchronizationStatus.NOT_SYNCHRONIZED,
          NodeSynchronizationStatus.SYNCHRONIZING,
          NodeSynchronizationStatus.SYNCHRONIZATION_FAILED
        ].some((s) => s === syncStatus);
        this.synchronizationCollapse$.next(syncCollapse);
      }
    );

    this.subscription.add(
      combineLatest([this.ports.activePortStates$, this.processes.processStates$]).subscribe(([activePortStates, states]) => {
        this.updateAllProcesses(states);
        this.updateAllPortsRating(activePortStates);
      })
    );

    this.subscription.add(
      combineLatest([this.collapsedWhen$, this.synchronizationCollapse$]).subscribe(([c, s]) => {
        this.collapsed$.next(c || s);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private getNodeStateItem(state: MinionStatusDto): StateItem {
    const syncStatus = state.nodeSynchronizationStatus;
    const isUnknownOffline = syncStatus === NodeSynchronizationStatus.UNKNOWN && state.offline;
    const isUnknownOnline = syncStatus === NodeSynchronizationStatus.UNKNOWN && !state.offline;

    if (syncStatus === NodeSynchronizationStatus.NOT_SYNCHRONIZED || isUnknownOffline) {
      return {
        name: 'Offline',
        type: 'warning'
      };
    }

    if (syncStatus === NodeSynchronizationStatus.SYNCHRONIZED || isUnknownOnline) {
      return {
        name: 'Online',
        type: 'ok'
      };
    }

    return {
      name: syncStatus
        .split('_')
        .map((word) => word.charAt(0).toUpperCase() + word.substring(1).toLowerCase())
        .join(' '),
      type: 'warning'
    };
  }

  protected onManualRefresh() {
    this.processes.reload();
    this.instances.reloadActiveStates(this.instances.active$.value);
  }

  private updateAllProcesses(states: MappedInstanceProcessStatusDto) {
    if (!states || Object.keys(states).length === 0) {
      this.processesState.next('unknown');
      this.processesTooltip.next('No information available');
      return;
    }

    let runningAliveApps = 0;
    let runningDeadApps = 0;
    let stoppedApps = 0;
    this.node.nodeConfiguration.applications.forEach((app) => {
      if (app.processControl.startType !== ApplicationStartType.INSTANCE) {
        return;
      }

      const statePerEachNode = ProcessesService.getAppStates(states, app.id) ?? {};
      Object.entries(statePerEachNode).forEach(([runtimeNode, statusDto]) => {
        if (!ProcessesService.isRunning(statusDto.processState)) {
          stoppedApps++;
        } else if (statusDto.processState === ProcessState.RUNNING_NOT_ALIVE) {
          runningDeadApps++;
        } else {
          runningAliveApps++;
        }
      });
    });

    this.updateProcessStateItem(runningAliveApps, runningDeadApps, stoppedApps);
  }

  private updateProcessStateItem(runningAliveApps: number, runningDeadApps: number, stoppedApps: number) {
    this.processesState.next(runningDeadApps ? 'warning' : stoppedApps ? 'info' : 'ok');
    this.processesTooltip.next(
      !runningAliveApps && !runningDeadApps
        ? 'The instance is stopped'
        : !stoppedApps && !runningDeadApps
          ? 'All applications are running without problems'
          : `${stoppedApps} 'Instance' type ${
            stoppedApps === 1 ? 'application is' : 'applications are'
          } not running\n${runningDeadApps} 'Instance' type ${
            runningDeadApps === 1 ? 'application is reporting' : 'applications are reporting'
          } problems`
    );
  }

  private updateAllPortsRating(activePortStates: Record<string, ApplicationPortStatesDto>) {
    if (!activePortStates) {
      this.portsState.next('unknown');
      this.portsTooltip.next('No information available');
      return;
    }

    let badPorts = 0;
    let totalPorts = 0;
    this.node.nodeConfiguration.applications.forEach(appConfig => {
      activePortStates[appConfig.id]?.portStates.forEach(compositeState => {
        totalPorts++;
        // assuming that just one state is there for this node
        const portState = compositeState.states[0];
        if (ProcessesService.isRunning(portState.processState) !== portState.isUsed) {
          badPorts++;
        }
      });
    });

    this.portsState.next(!badPorts ? 'ok' : 'warning');
    this.portsTooltip.next(
      !badPorts
        ? `All ${totalPorts} server ports OK`
        : `${badPorts} of ${totalPorts} server ports are rated bad.`
    );
  }
}
