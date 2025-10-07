import { Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceNodeConfigurationDto,
  MappedInstanceProcessStatusDto,
  NodeSynchronizationStatus,
  NodeType
} from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesService } from '../../../services/processes.service';
import { NodeStatePanelComponent, StateItem } from '../state-panel/state-panel.component';
import { MatDivider } from '@angular/material/divider';
import { AsyncPipe } from '@angular/common';
import { NodeProcessListComponent } from '../server-node/process-list/process-list.component';

@Component({
  selector: 'app-instance-multi-node',
  templateUrl: './multi-node.component.html',
  styleUrls: ['./multi-node.component.css'],
  imports: [MatDivider, AsyncPipe, NodeStatePanelComponent, NodeProcessListComponent]
})
export class MultiNodeComponent implements OnInit, OnDestroy {
  private readonly instances = inject(InstancesService);
  private readonly auth = inject(AuthenticationService);
  protected readonly areas = inject(NavAreasService);
  protected readonly processes = inject(ProcessesService);

  @Input() node: InstanceNodeConfigurationDto;

  @Input() bulkMode: boolean;
  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;
  @Input() collapsedWhen$: BehaviorSubject<boolean>;
  @Input() narrowWhen$: BehaviorSubject<boolean>;

  protected nodeStateItems$ = new BehaviorSubject<StateItem[]>([]);
  protected synchronizationCollapse$ = new BehaviorSubject<boolean>(false);
  protected collapsed$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.instances.activeNodeStates$, this.instances.productUpdates$, this.processes.processStates$]).subscribe(
      ([states, updates, processStates]) => {
        if (!states?.[this.node.nodeName]) {
          this.nodeStateItems$.next([]);
          return;
        }
        const minionState = states[this.node.nodeName];
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

        items.push(this.getNodeCountItem(this.node.nodeName, processStates));

        const syncStatus = minionState.nodeSynchronizationStatus;
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
      combineLatest([this.collapsedWhen$, this.synchronizationCollapse$]).subscribe(([c, s]) => {
        this.collapsed$.next(c || s);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private getNodeCountItem(multiNodeName: string, processState: MappedInstanceProcessStatusDto): StateItem {
    const runtimeNodeCount = processState?.multiNodeToRuntimeNode[multiNodeName]?.length ?? 0;
    return {
      name: `${runtimeNodeCount} registered`,
      type: 'info'
    };
  }

  protected onManualRefresh() {
    this.processes.reload();
    this.instances.reloadActiveStates(this.instances.active$.value);
  }

  protected readonly NodeType = NodeType;
}
