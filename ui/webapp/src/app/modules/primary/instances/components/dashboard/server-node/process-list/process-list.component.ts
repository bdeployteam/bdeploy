import {
  AfterViewInit,
  Component,
  Input,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { BehaviorSubject, filter, Subscription } from 'rxjs';
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
import { InstanceEditService } from '../../../../services/instance-edit.service';
import { InstancesService } from '../../../../services/instances.service';
import { ProcessesBulkService } from '../../../../services/processes-bulk.service';
import { ProcessesColumnsService } from '../../../../services/processes-columns.service';
import { PortsService } from './../../../../services/ports.service';

interface PinnedParameter {
  name: string;
  value: string;
}

@Component({
  selector: 'app-node-process-list',
  templateUrl: './process-list.component.html',
})
export class NodeProcessListComponent
  implements OnInit, AfterViewInit, OnDestroy
{
  private processCtrlGroupColumn: BdDataColumn<ApplicationConfiguration> = {
    id: 'ctrlGroup',
    name: 'Control Group',
    data: (r) => this.getControlGroup(r),
    width: '120px',
    showWhen: '(min-width:1410px)',
  };

  @Input() node: InstanceNodeConfigurationDto;

  @Input() bulkMode: boolean;
  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<
    BdDataGrouping<ApplicationConfiguration>[]
  >;

  /* template */ columns = [...this.appCols.defaultProcessesColumns];

  /* template */ getRecordRoute = (row: ApplicationConfiguration) => {
    return [
      '',
      { outlets: { panel: ['panels', 'instances', 'process', row.id] } },
    ];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue = 'processList';

  @ViewChild(BdDataDisplayComponent)
  private data: BdDataDisplayComponent<ApplicationConfiguration>;
  private subscription: Subscription;
  private nodes: InstanceNodeConfigurationListDto;

  constructor(
    private appCols: ProcessesColumnsService,
    private cardViewService: CardViewService,
    public bulk: ProcessesBulkService,
    private ports: PortsService,
    private instances: InstancesService,
    private edit: InstanceEditService,
    private systems: SystemsService
  ) {
    this.columns.splice(2, 0, this.processCtrlGroupColumn);
  }

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
    this.instances.activeNodeCfgs$
      .pipe(filter((i) => !!i))
      .subscribe((nodes) => (this.nodes = nodes));
  }

  ngAfterViewInit(): void {
    // active ports states reacts to *all* other state changes, so we use this as update trigger.
    this.subscription = this.ports.activePortStates$.subscribe(() =>
      this.data.redraw()
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  getControlGroup(row: ApplicationConfiguration): string {
    return this.node.nodeConfiguration.controlGroups.find((cg) =>
      cg.processOrder.includes(row.id)
    )?.name;
  }

  /* template */ getPinnedParameters(
    record: ApplicationConfiguration
  ): PinnedParameter[] {
    const app = this.nodes?.applications?.find(
      (a) =>
        a.key.name === record.application?.name &&
        a.key.tag === record.application?.tag
    );
    const params = app?.descriptor?.startCommand?.parameters;
    return record.start.parameters
      .filter((p) => p.pinned)
      .map((p) => ({
        name: params?.find((x) => x.id === p.id)?.name,
        value: this.getPinnedParameterValue(record, p),
      }));
  }

  getPinnedParameterValue(
    record: ApplicationConfiguration,
    p: ParameterConfiguration
  ): string {
    const system = this.edit.state$?.value?.config?.config?.system
      ? this.systems.systems$.value?.find(
          (s) => s.key.name === this.edit.state$.value.config.config.system.name
        )
      : null;
    return getRenderPreview(
      p.value,
      record,
      this.edit.state$.value?.config,
      system?.config
    );
  }

  /* template */ doTrack(index: number, param: PinnedParameter) {
    return param.name;
  }
}
