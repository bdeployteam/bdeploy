import { Component, OnInit } from '@angular/core';
import { DeviceDetectorService } from 'ngx-device-detector';
import {
  BdDataColumn,
  BdDataColumnDisplay,
  BdDataColumnTypeHint,
  BdDataGrouping,
  BdDataGroupingDefinition,
} from 'src/app/models/data';
import { OperatingSystem } from 'src/app/models/gen.dtos';
import { BdDataSvgIconCellComponent } from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ClientApp, ClientsService } from '../../services/clients.service';
import { GroupsService } from '../../services/groups.service';

const clientNameColumn: BdDataColumn<ClientApp> = {
  id: 'name',
  name: 'Client Name',
  data: (r) =>
    r.client
      ? r.client.description
      : `${r.endpoint.appName} - ${r.endpoint.endpoint.id}`,
  hint: BdDataColumnTypeHint.TITLE,
};

const clientIdColumn: BdDataColumn<ClientApp> = {
  id: 'id',
  name: 'App. ID',
  data: (r) => (r.client ? r.client.id : r.endpoint.id),
  isId: true,
  hint: BdDataColumnTypeHint.DETAILS,
  icon: () => 'computer',
};

const clientInstanceColumn: BdDataColumn<ClientApp> = {
  id: 'instance',
  name: 'Instance Name',
  data: (r) => r.instanceName,
  hint: BdDataColumnTypeHint.DESCRIPTION,
};

const clientOsColumn: BdDataColumn<ClientApp> = {
  id: 'os',
  name: 'OS',
  data: (r) => (r.client ? r.client.os : 'WEB'),
  display: BdDataColumnDisplay.TABLE,
  component: BdDataSvgIconCellComponent,
};

const clientAvatarColumn: BdDataColumn<ClientApp> = {
  id: 'osAvatar',
  name: 'OS',
  hint: BdDataColumnTypeHint.AVATAR,
  data: (r) => `/assets/${r.client ? r.client.os.toLowerCase() : 'web'}.svg`,
  display: BdDataColumnDisplay.CARD,
};

@Component({
  selector: 'app-client-applications',
  templateUrl: './client-applications.component.html',
})
export class ClientApplicationsComponent implements OnInit {
  /* template */ currentOs: OperatingSystem;
  /* template */ columns: BdDataColumn<ClientApp>[] = [
    clientNameColumn,
    clientIdColumn,
    clientInstanceColumn,
    clientOsColumn,
    clientAvatarColumn,
  ];

  /* template */ grouping: BdDataGroupingDefinition<ClientApp>[] = [
    {
      name: 'Instance Name',
      group: (r) => r.instanceName,
      associatedColumn: clientInstanceColumn.id,
    },
    {
      name: 'Operating System',
      group: (r) => (r.client ? r.client.os : 'WEB'),
    },
  ];
  /* template */ defaultGrouping: BdDataGrouping<ClientApp>[];

  /* template */ getRecordRoute = (row: ClientApp) => {
    if (row.client) {
      return [
        '',
        { outlets: { panel: ['panels', 'groups', 'client', row.client.id] } },
      ];
    } else {
      return [
        '',
        {
          outlets: {
            panel: [
              'panels',
              'groups',
              'endpoint-detail',
              row.endpoint.id,
              row.endpoint.endpoint.id,
            ],
          },
        },
      ];
    }
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue = 'clientApplications';

  constructor(
    public groups: GroupsService,
    public clients: ClientsService,
    dd: DeviceDetectorService,
    private cardViewService: CardViewService
  ) {
    this.currentOs = (() => {
      switch (dd.os) {
        case 'Windows':
          return OperatingSystem.WINDOWS;
        case 'Linux':
          return OperatingSystem.LINUX;
        case 'Mac':
          return OperatingSystem.MACOS;
      }
    })();

    this.defaultGrouping = [
      { definition: this.grouping[0], selected: [] },
      { definition: this.grouping[1], selected: [this.currentOs, 'WEB'] },
    ];
  }

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
