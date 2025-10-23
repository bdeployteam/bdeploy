import { AfterViewInit, Component, inject, Input, OnDestroy, OnInit, signal, ViewChild } from '@angular/core';
import { BehaviorSubject, filter, Subscription } from 'rxjs';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceNodeConfigurationDto,
  InstanceNodeConfigurationListDto,
  NodeType,
  ParameterConfiguration
} from 'src/app/models/gen.dtos';
import { BdDataDisplayComponent } from 'src/app/modules/core/components/bd-data-display/bd-data-display.component';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { InstancesService } from '../../../../services/instances.service';
import { ProcessesBulkService } from '../../../../services/processes-bulk.service';
import { ProcessDisplayData, ProcessesColumnsService } from '../../../../services/processes-columns.service';

import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe } from '@angular/common';
import { ProcessesService } from '../../../../services/processes.service';

interface PinnedParameter {
  name: string;
  value: string;
}

export const CONTROL_GROUP_COL_ID = 'ctrlGroup';

@Component({
  selector: 'app-node-process-list',
  templateUrl: './process-list.component.html',
  imports: [BdDataDisplayComponent, MatIcon, MatTooltip, AsyncPipe]
})
export class NodeProcessListComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly appCols = inject(ProcessesColumnsService);
  private readonly cardViewService = inject(CardViewService);
  private readonly processesService = inject(ProcessesService);
  private readonly instances = inject(InstancesService);
  private readonly systems = inject(SystemsService);
  protected readonly bulk = inject(ProcessesBulkService);

  private readonly processCtrlGroupColumn: BdDataColumn<ProcessDisplayData, string> = {
    id: CONTROL_GROUP_COL_ID,
    name: 'Control Group',
    data: (r) => this.getControlGroup(r),
    width: '120px',
    showWhen: '(min-width:1410px)'
  };

  @Input() node: InstanceNodeConfigurationDto;
  @Input({ required: false }) composite = false;
  @Input() bulkMode: boolean;
  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ProcessDisplayData>[]>;

  @ViewChild(BdDataDisplayComponent) private readonly data: BdDataDisplayComponent<ProcessDisplayData>;

  protected processList = signal([]);
  protected columns = [...this.appCols.defaultProcessesColumns];

  protected getRecordRoute = (row: ProcessDisplayData) => {
    if (this.composite) {
      return [
        '',
        { outlets: { panel: ['panels', 'instances', 'multi-node-process', row.id] } }
      ];
    } else {
      return [
        '',
        { outlets: { panel: ['panels', 'instances', 'process', row.id] } }
      ];
    }
  };

  protected isCardView: boolean;
  protected presetKeyValue = 'processList';

  private subscription: Subscription;
  private nodes: InstanceNodeConfigurationListDto;

  ngOnInit(): void {
    this.columns.splice(2, 0, this.processCtrlGroupColumn);
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
    this.instances.activeNodeCfgs$.pipe(filter((i) => !!i)).subscribe((nodes) => (this.nodes = nodes));
  }

  ngAfterViewInit(): void {
    this.subscription = this.processesService.processStates$.subscribe((instanceProcessStates) => {
      const processList: ProcessDisplayData[] = [];
      this.node.nodeConfiguration.applications.forEach(appConfig => {
        if (this.node.nodeConfiguration.nodeType == NodeType.MULTI) {
          if (this.composite) {
            processList.push(appConfig);
          } else {
            instanceProcessStates?.multiNodeToRuntimeNode[this.node.nodeName]?.forEach(runtimeNode => {
              processList.push({ ...appConfig, serverNode: runtimeNode });
            });
          }
        } else {
          processList.push({ ...appConfig, serverNode: this.node.nodeName });
        }
      });

      this.processList.set(processList);
      this.data.redraw();
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  getControlGroup(row: ApplicationConfiguration): string {
    return this.node.nodeConfiguration.controlGroups.find((cg) => cg.processOrder.includes(row.id))?.name;
  }

  protected getPinnedParameters(record: ApplicationConfiguration): PinnedParameter[] {
    const app = this.nodes?.applications?.find(
      (a) => a.key.name === record.application?.name && a.key.tag === record.application?.tag
    );
    const params = app?.descriptor?.startCommand?.parameters;
    return record.start.parameters
      .filter((p) => p.pinned)
      .map((p) => ({
        name: params?.find((x) => x.id === p.id)?.name,
        value: this.getPinnedParameterValue(record, p)
      }));
  }

  private getPinnedParameterValue(record: ApplicationConfiguration, p: ParameterConfiguration): string {
    const instanceConfiguration = this.instances.active$.value?.instanceConfiguration;

    const system = instanceConfiguration?.system
      ? this.systems.systems$.value?.find((s) => s.key.name === instanceConfiguration.system.name)
      : null;

    const nodeDtos = this.instances.activeNodeCfgs$.value?.nodeConfigDtos;

    return getRenderPreview(
      p.value,
      record,
      {
        config: instanceConfiguration,
        nodeDtos
      },
      system?.config
    );
  }

  protected doTrack(index: number, param: PinnedParameter) {
    return param.name;
  }
}
