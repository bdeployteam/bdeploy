import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import { ApplicationConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
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

  constructor(private appCols: ProcessesColumnsService) {}

  ngOnInit(): void {}
}
