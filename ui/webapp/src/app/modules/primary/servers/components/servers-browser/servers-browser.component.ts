import { Component, OnInit } from '@angular/core';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { ServersColumnsService } from '../../services/servers-columns.service';
import { ServersService } from '../../services/servers.service';

@Component({
  selector: 'app-servers-browser',
  templateUrl: './servers-browser.component.html',
})
export class ServersBrowserComponent implements OnInit {
  grouping: BdDataGroupingDefinition<ManagedMasterDto>[] = [];

  /* template */ getRecordRoute = (row: ManagedMasterDto) => {
    return [
      '',
      { outlets: { panel: ['panels', 'servers', 'details', row.hostName] } },
    ];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue = 'managedServers';

  constructor(
    public groups: GroupsService,
    public servers: ServersService,
    public columns: ServersColumnsService,
    private cardViewService: CardViewService
  ) {}

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
