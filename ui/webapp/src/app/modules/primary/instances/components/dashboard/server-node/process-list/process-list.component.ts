import {
  AfterViewInit,
  Component,
  Input,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceNodeConfigurationDto,
} from 'src/app/models/gen.dtos';
import { BdDataDisplayComponent } from 'src/app/modules/core/components/bd-data-display/bd-data-display.component';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ProcessesBulkService } from '../../../../services/processes-bulk.service';
import { ProcessesColumnsService } from '../../../../services/processes-columns.service';
import { PortsService } from './../../../../services/ports.service';

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

  constructor(
    private appCols: ProcessesColumnsService,
    private cardViewService: CardViewService,
    public bulk: ProcessesBulkService,
    private ports: PortsService
  ) {
    this.columns.splice(2, 0, this.processCtrlGroupColumn);
  }

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
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
}
