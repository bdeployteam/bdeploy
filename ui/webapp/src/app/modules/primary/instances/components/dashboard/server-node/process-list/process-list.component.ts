import { AfterViewInit, Component, Input, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription, filter } from 'rxjs';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceNodeConfigurationDto,
  InstanceNodeConfigurationListDto,
  ParameterConfiguration,
} from 'src/app/models/gen.dtos';
import { BdDataDisplayComponent } from 'src/app/modules/core/components/bd-data-display/bd-data-display.component';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { InstancesService } from '../../../../services/instances.service';
import { ProcessesBulkService } from '../../../../services/processes-bulk.service';
import { ProcessesColumnsService } from '../../../../services/processes-columns.service';
import { PortsService } from './../../../../services/ports.service';

interface PinnedParameter {
  name: string;
  value: string;
}

export const CONTROL_GROUP_COL_ID = 'ctrlGroup';
@Component({
  selector: 'app-node-process-list',
  templateUrl: './process-list.component.html',
})
export class NodeProcessListComponent implements OnInit, AfterViewInit, OnDestroy {
  private appCols = inject(ProcessesColumnsService);
  private cardViewService = inject(CardViewService);
  private ports = inject(PortsService);
  private instances = inject(InstancesService);
  private systems = inject(SystemsService);
  protected bulk = inject(ProcessesBulkService);

  private processCtrlGroupColumn: BdDataColumn<ApplicationConfiguration> = {
    id: CONTROL_GROUP_COL_ID,
    name: 'Control Group',
    data: (r) => this.getControlGroup(r),
    width: '120px',
    showWhen: '(min-width:1410px)',
  };

  @Input() node: InstanceNodeConfigurationDto;
  @Input() bulkMode: boolean;
  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;

  @ViewChild(BdDataDisplayComponent) private data: BdDataDisplayComponent<ApplicationConfiguration>;

  protected columns = [...this.appCols.defaultProcessesColumns];

  protected getRecordRoute = (row: ApplicationConfiguration) => [
    '',
    { outlets: { panel: ['panels', 'instances', 'process', row.id] } },
  ];

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
    // active ports states reacts to *all* other state changes, so we use this as update trigger.
    this.subscription = this.ports.activePortStates$.subscribe(() => this.data.redraw());
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  getControlGroup(row: ApplicationConfiguration): string {
    return this.node.nodeConfiguration.controlGroups.find((cg) => cg.processOrder.includes(row.id))?.name;
  }

  protected getPinnedParameters(record: ApplicationConfiguration): PinnedParameter[] {
    const app = this.nodes?.applications?.find(
      (a) => a.key.name === record.application?.name && a.key.tag === record.application?.tag,
    );
    const params = app?.descriptor?.startCommand?.parameters;
    return record.start.parameters
      .filter((p) => p.pinned)
      .map((p) => ({
        name: params?.find((x) => x.id === p.id)?.name,
        value: this.getPinnedParameterValue(record, p),
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
        nodeDtos,
      },
      system?.config,
    );
  }

  protected doTrack(index: number, param: PinnedParameter) {
    return param.name;
  }
}
