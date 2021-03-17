import { Component, OnDestroy, OnInit } from '@angular/core';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { GroupsService } from '../../../groups/services/groups.service';
import { ServersColumnsService } from '../../services/servers-columns.service';
import { ServersService } from '../../services/servers.service';

@Component({
  selector: 'app-servers-browser',
  templateUrl: './servers-browser.component.html',
  styleUrls: ['./servers-browser.component.css'],
})
export class ServersBrowserComponent implements OnInit, OnDestroy {
  grouping: BdDataGroupingDefinition<ManagedMasterDto>[] = [];

  /* template */ getRecordRoute = (row: ManagedMasterDto) => {
    return ['', { outlets: { panel: ['panels', 'servers', 'details', row.hostName] } }];
  };

  constructor(public groups: GroupsService, public servers: ServersService, public columns: ServersColumnsService) {}

  ngOnInit(): void {}

  ngOnDestroy(): void {}
}
