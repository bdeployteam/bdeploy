import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import { ApplicationConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ProcessesColumnsService } from '../../../../services/processes-columns.service';

@Component({
  selector: 'app-node-process-list',
  templateUrl: './process-list.component.html',
  styleUrls: ['./process-list.component.css'],
})
export class NodeProcessListComponent implements OnInit {
  @Input() node: InstanceNodeConfigurationDto;

  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;

  /* template */ columns = this.appCols.defaultProcessesColumns;

  /* template */ getRecordRoute = (row: ApplicationConfiguration) => {
    return ['', { outlets: { panel: ['panels', 'instances', 'process', row.uid] } }];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue: string = 'processList';

  constructor(private appCols: ProcessesColumnsService, private cardViewService: CardViewService) {}

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
