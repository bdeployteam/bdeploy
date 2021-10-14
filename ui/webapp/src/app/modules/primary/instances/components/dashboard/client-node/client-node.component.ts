import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import { ApplicationConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { ClientsService } from 'src/app/modules/primary/groups/services/clients.service';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesColumnsService } from '../../../services/processes-columns.service';

@Component({
  selector: 'app-instance-client-node',
  templateUrl: './client-node.component.html',
  styleUrls: ['./client-node.component.css'],
})
export class ClientNodeComponent implements OnInit {
  @Input() node: InstanceNodeConfigurationDto;

  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;

  /* template */ columns = this.appCols.defaultProcessClientColumns;

  /* template */ getRecordRoute = (row: ApplicationConfiguration) => {
    return ['', { outlets: { panel: ['panels', 'groups', 'client', row.uid] } }];
  };

  constructor(public instances: InstancesService, public clients: ClientsService, private appCols: ProcessesColumnsService) {}

  ngOnInit(): void {}
}
