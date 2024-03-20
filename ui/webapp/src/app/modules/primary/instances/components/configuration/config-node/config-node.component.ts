import { moveItemInArray } from '@angular/cdk/drag-drop';
import {
  AfterViewInit,
  Component,
  HostBinding,
  Input,
  OnDestroy,
  OnInit,
  QueryList,
  ViewChildren,
  inject,
} from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceNodeConfigurationDto,
  MinionDto,
  ProcessControlGroupConfiguration,
} from 'src/app/models/gen.dtos';
import {
  BdDataTableComponent,
  DragReorderEvent,
} from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { DEF_CONTROL_GROUP } from 'src/app/modules/panels/instances/utils/instance-utils';
import { InstanceEditService, ProcessEditState } from '../../../services/instance-edit.service';
import { ProcessesColumnsService } from '../../../services/processes-columns.service';
import { ProcessNameAndOsComponent } from '../../process-name-and-os/process-name-and-os.component';

@Component({
  selector: 'app-config-node',
  templateUrl: './config-node.component.html',
  styleUrls: ['./config-node.component.css'],
})
export class ConfigNodeComponent implements OnInit, OnDestroy, AfterViewInit {
  private edit = inject(InstanceEditService);
  protected columns = inject(ProcessesColumnsService);

  @HostBinding('attr.data-cy') @Input() nodeName: string;

  private processNameAndEditStatusColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    classes: (r) => this.getStateClass(r),
  };

  private processNameAndOsAndEditStatusColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'name',
    name: 'Name and OS',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    classes: (r) => this.getStateClass(r),
    component: ProcessNameAndOsComponent,
  };

  protected node$ = new BehaviorSubject<MinionDto>(null);
  protected config$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  protected groupedProcesses$ = new BehaviorSubject<{
    [key: string]: ApplicationConfiguration[];
  }>(null);
  protected allowedSources$ = new BehaviorSubject<string[]>(null);
  protected isClientNode: boolean;
  protected nodeType: string;
  protected node: string;
  protected groupExpansion: { [key: string]: boolean } = {};
  protected lastId: string;
  protected clientTableId = CLIENT_NODE_NAME + '||' + DEF_CONTROL_GROUP.name;

  protected cols: BdDataColumn<ApplicationConfiguration>[] = [];

  @ViewChildren(BdDataTableComponent) private data: QueryList<BdDataTableComponent<ApplicationConfiguration>>;

  private subscription: Subscription;

  protected getRecordRoute = (row: ApplicationConfiguration) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'instances', 'config', 'process', this.nodeName, row.id],
        },
      },
    ];
  };

  ngOnInit(): void {
    this.subscription = this.edit.nodes$.subscribe((nodes) => {
      if (!nodes || !nodes[this.nodeName]) {
        this.node$.next(null);
      } else {
        this.node$.next(nodes[this.nodeName]);
      }
      this.isClientNode = this.nodeName === CLIENT_NODE_NAME;
      this.nodeType = this.isClientNode ? 'Virtual Node' : 'Node';
      this.node = this.isClientNode ? 'Client Applications' : this.nodeName;

      if (this.isClientNode) {
        this.cols = [this.processNameAndOsAndEditStatusColumn, ...this.columns.defaultProcessesConfigColumns];
      } else {
        this.cols = [this.processNameAndEditStatusColumn, ...this.columns.defaultProcessesConfigColumns];
      }
    });

    this.subscription.add(
      combineLatest([this.edit.validating$, this.edit.issues$]).subscribe(() => {
        // update in case validation is run in the background - this means something may have changed
        this.data?.forEach((t) => t?.redraw());
      }),
    );
  }

  ngAfterViewInit(): void {
    this.subscription.add(
      this.edit.state$.subscribe((s) => {
        setTimeout(() => {
          const nodeConfig = s?.config?.nodeDtos?.find((n) => n.nodeName === this.nodeName);
          this.config$.next(nodeConfig);

          if (!nodeConfig) {
            this.allowedSources$.next(null);
            this.groupedProcesses$.next(null);
            return;
          }

          // reset expansion state in case we switch the instance somehow.
          if (this.lastId !== nodeConfig.nodeConfiguration.id) {
            this.groupExpansion = {};
            this.lastId = nodeConfig.nodeConfiguration.id;
          }

          const grouped = {};
          // eslint-disable-next-line no-unsafe-optional-chaining
          for (const app of nodeConfig?.nodeConfiguration?.applications) {
            let group = this.getControlGroup(app);
            if (!group) {
              console.error('no control group found for', app);
              // this should not happen - we still need to see the application in the UI, put it in default.
              group = 'Default';
            }
            const apps = grouped[group] || [];
            apps.push(app);
            grouped[group] = apps;
          }

          for (const group of nodeConfig.nodeConfiguration.controlGroups) {
            if (this.groupExpansion[group.name] === undefined) {
              this.groupExpansion[group.name] = true;
            }
          }

          // don't use groupedProcesses keys, as this will not contain *empty* groups.
          this.allowedSources$.next(
            nodeConfig.nodeConfiguration.controlGroups.map((cg) => this.nodeName + '||' + cg.name),
          );
          this.groupedProcesses$.next(grouped);
          this.data.forEach((t) => t.update());
        });
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onReorder(order: DragReorderEvent<ApplicationConfiguration>) {
    if (order.previousIndex === order.currentIndex && order.sourceId === order.targetId) {
      return;
    }

    // ID contains the node name so that dragging between nodes is not possible.
    const sourceGroup = order.sourceId?.split('||')[1];
    const targetGroup = order.targetId?.split('||')[1];

    if (order.sourceId === order.targetId) {
      // this is NOT necessary, but prevents flickering while rebuilding state.
      moveItemInArray(this.groupedProcesses$.value[sourceGroup], order.previousIndex, order.currentIndex);
    }

    this.edit.conceal(
      `Re-arrange ${order.item.name}`,
      this.edit.createApplicationMove(
        this.config$.value.nodeName,
        order.previousIndex,
        order.currentIndex,
        sourceGroup,
        targetGroup,
      ),
    );
    this.data.forEach((t) => t.update());
  }

  private getControlGroup(row: ApplicationConfiguration): string {
    return this.config$.value.nodeConfiguration.controlGroups.find((cg) => cg.processOrder.includes(row.id))?.name;
  }

  protected doTrack(index: number, group: ProcessControlGroupConfiguration) {
    return group.name;
  }

  private getStateClass(r: ApplicationConfiguration) {
    switch (this.edit.getProcessEditState(r.id)) {
      case ProcessEditState.ADDED:
        return ['bd-status-border-added'];
      case ProcessEditState.INVALID:
        return ['bd-status-border-invalid'];
      case ProcessEditState.CHANGED:
        return ['bd-status-border-changed'];
    }
    return ['bd-status-border-none'];
  }
}
