import { Component, OnInit, inject } from '@angular/core';
import { MinionMode, SystemConfigurationDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { SystemsColumnsService } from '../../services/systems-columns.service';
import { SystemsService } from '../../services/systems.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';
import { BdDataColumn } from '../../../../../models/data';

@Component({
    selector: 'app-system-browser',
    templateUrl: './system-browser.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, MatDivider, BdPanelButtonComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent, AsyncPipe]
})
export class SystemBrowserComponent implements OnInit {
  private readonly columns = inject(SystemsColumnsService);
  private readonly config = inject(ConfigService);
  protected readonly systems = inject(SystemsService);
  protected readonly groups = inject(GroupsService);
  protected readonly authService = inject(AuthenticationService);

  protected sysCols: BdDataColumn<SystemConfigurationDto, unknown>[] = [
    this.columns.systemNameColumn,
    this.columns.systemDescriptionColumn,
    this.columns.systemVarsColumn,
    this.columns.systemIdColumn,
  ];

  protected getRecordRoute = (row: SystemConfigurationDto) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'systems', 'details', row.key.name],
        },
      },
    ];
  };

  protected gridMode: boolean;

  ngOnInit() {
    if (this.config.config.mode === MinionMode.CENTRAL) {
      this.sysCols.push(this.columns.systemMinionColumn);
      this.sysCols.push(this.columns.systemSyncColumn);
    }
  }
}
