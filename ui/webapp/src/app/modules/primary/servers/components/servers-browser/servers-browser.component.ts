import { Component, OnInit, inject } from '@angular/core';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { ServersColumnsService } from '../../services/servers-columns.service';
import { ServersService } from '../../services/servers.service';

@Component({
    selector: 'app-servers-browser',
    templateUrl: './servers-browser.component.html',
    standalone: false
})
export class ServersBrowserComponent implements OnInit {
  private readonly cardViewService = inject(CardViewService);
  protected readonly groups = inject(GroupsService);
  protected readonly servers = inject(ServersService);
  protected readonly columns = inject(ServersColumnsService);

  grouping: BdDataGroupingDefinition<ManagedMasterDto>[] = [];

  protected getRecordRoute = (row: ManagedMasterDto) => [
    '',
    { outlets: { panel: ['panels', 'servers', 'details', row.hostName] } },
  ];

  protected isCardView: boolean;
  protected presetKeyValue = 'managedServers';

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
