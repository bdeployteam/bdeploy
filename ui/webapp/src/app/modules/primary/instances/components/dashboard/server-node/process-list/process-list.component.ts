import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceNodeConfigurationDto,
} from 'src/app/models/gen.dtos';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ProcessesBulkService } from '../../../../services/processes-bulk.service';
import { ProcessesColumnsService } from '../../../../services/processes-columns.service';

@Component({
  selector: 'app-node-process-list',
  templateUrl: './process-list.component.html',
})
export class NodeProcessListComponent implements OnInit {
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
      { outlets: { panel: ['panels', 'instances', 'process', row.uid] } },
    ];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue = 'processList';

  constructor(
    private appCols: ProcessesColumnsService,
    private cardViewService: CardViewService,
    public bulk: ProcessesBulkService
  ) {
    this.columns.splice(2, 0, this.processCtrlGroupColumn);
  }

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }

  getControlGroup(row: ApplicationConfiguration): string {
    return this.node.nodeConfiguration.controlGroups.find((cg) =>
      cg.processOrder.includes(row.uid)
    )?.name;
  }
}
