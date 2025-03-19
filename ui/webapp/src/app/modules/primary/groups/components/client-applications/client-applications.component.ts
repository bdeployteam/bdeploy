import { Component, inject, OnInit } from '@angular/core';
import { DeviceDetectorService } from 'ngx-device-detector';
import { map } from 'rxjs';
import {
  BdDataColumn,
  BdDataColumnDisplay,
  BdDataColumnTypeHint,
  BdDataGrouping,
  BdDataGroupingDefinition
} from 'src/app/models/data';
import { OperatingSystem } from 'src/app/models/gen.dtos';
import {
  BdDataSvgIconCellComponent
} from 'src/app/modules/core/components/bd-data-svg-icon-cell/bd-data-svg-icon-cell.component';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ClientApp, ClientsService } from '../../services/clients.service';
import { GroupsService } from '../../services/groups.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDataGroupingComponent } from '../../../../core/components/bd-data-grouping/bd-data-grouping.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

const clientNameColumn: BdDataColumn<ClientApp, string> = {
  id: 'name',
  name: 'Client Name',
  data: (r) => (r.client ? r.client.description : `${r.endpoint.appName} - ${r.endpoint.endpoint.id}`),
  hint: BdDataColumnTypeHint.TITLE
};

const clientIdColumn: BdDataColumn<ClientApp, string> = {
  id: 'id',
  name: 'App. ID',
  data: (r) => (r.client ? r.client.id : r.endpoint.id),
  isId: true,
  hint: BdDataColumnTypeHint.DETAILS,
  icon: () => 'computer'
};

const clientInstanceColumn: BdDataColumn<ClientApp, string> = {
  id: 'instance',
  name: 'Instance Name',
  data: (r) => r.instanceName,
  hint: BdDataColumnTypeHint.DESCRIPTION
};

const clientOsColumn: BdDataColumn<ClientApp, string> = {
  id: 'os',
  name: 'OS',
  data: (r) => (r.client ? r.client.os : 'WEB'),
  display: BdDataColumnDisplay.TABLE,
  component: BdDataSvgIconCellComponent
};

const clientAvatarColumn: BdDataColumn<ClientApp, string> = {
  id: 'osAvatar',
  name: 'OS',
  hint: BdDataColumnTypeHint.AVATAR,
  data: (r) => `/assets/${r.client ? r.client.os.toLowerCase() : 'web'}.svg`,
  display: BdDataColumnDisplay.CARD
};

@Component({
    selector: 'app-client-applications',
    templateUrl: './client-applications.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDataGroupingComponent, BdButtonComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent, AsyncPipe]
})
export class ClientApplicationsComponent implements OnInit {
  private readonly dd = inject(DeviceDetectorService);
  private readonly cardViewService = inject(CardViewService);
  protected readonly groups = inject(GroupsService);
  protected readonly clients = inject(ClientsService);

  protected currentOs: OperatingSystem;
  protected readonly columns: BdDataColumn<ClientApp, unknown>[] = [
    clientNameColumn,
    clientIdColumn,
    clientInstanceColumn,
    clientOsColumn,
    clientAvatarColumn
  ];

  protected grouping: BdDataGroupingDefinition<ClientApp>[] = [
    {
      name: 'Instance Name',
      group: (r) => r.instanceName,
      associatedColumn: clientInstanceColumn.id
    },
    {
      name: 'Operating System',
      group: (r) => (r.client ? r.client.os : 'WEB')
    }
  ];
  protected defaultGrouping: BdDataGrouping<ClientApp>[];

  protected getRecordRoute = (row: ClientApp) => {
    if (row.client) {
      return ['', { outlets: { panel: ['panels', 'groups', 'client', row.client.id] } }];
    } else {
      return [
        '',
        {
          outlets: {
            panel: ['panels', 'groups', 'endpoint-detail', row.endpoint.id, row.endpoint.endpoint.id]
          }
        }
      ];
    }
  };

  protected isCardView: boolean;
  protected presetKeyValue = 'clientApplications';

  protected readonly filteredApps$ = this.clients.apps$.pipe(
    map((apps) =>
      apps.filter((app) => {
        if (app.endpoint) {
          return app.endpoint.endpointEnabledPreresolved;
        }
        return true;
      })
    )
  );

  ngOnInit(): void {
    this.currentOs = (() => {
      switch (this.dd.os) {
        case 'Windows':
          return OperatingSystem.WINDOWS;
        case 'Linux':
          return OperatingSystem.LINUX;
        case 'Mac':
          return OperatingSystem.MACOS;
      }
      return null;
    })();

    this.defaultGrouping = [
      { definition: this.grouping[0], selected: [] },
      { definition: this.grouping[1], selected: [this.currentOs, 'WEB'] }
    ];

    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
