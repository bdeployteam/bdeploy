import { Component, OnInit, inject } from '@angular/core';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { ServersColumnsService } from '../../services/servers-columns.service';
import { ServersService } from '../../services/servers.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDataGroupingComponent } from '../../../../core/components/bd-data-grouping/bd-data-grouping.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-servers-browser',
    templateUrl: './servers-browser.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDataGroupingComponent, BdButtonComponent, MatDivider, BdPanelButtonComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent, AsyncPipe]
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
