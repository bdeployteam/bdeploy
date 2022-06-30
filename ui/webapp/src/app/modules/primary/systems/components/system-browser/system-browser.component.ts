import { Component } from '@angular/core';
import { MinionMode, SystemConfigurationDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { SystemsColumnsService } from '../../services/systems-columns.service';
import { SystemsService } from '../../services/systems.service';

@Component({
  selector: 'app-system-browser',
  templateUrl: './system-browser.component.html',
})
export class SystemBrowserComponent {
  /* template */ sysCols = [
    this.columns.systemNameColumn,
    this.columns.systemDescriptionColumn,
    this.columns.systemVarsColumn,
    this.columns.systemIdColumn,
  ];

  /* template */ getRecordRoute = (row: SystemConfigurationDto) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'systems', 'details', row.key.name],
        },
      },
    ];
  };

  /* template */ gridMode: boolean;

  constructor(
    public systems: SystemsService,
    public groups: GroupsService,
    public authService: AuthenticationService,
    private columns: SystemsColumnsService,
    config: ConfigService
  ) {
    if (config.config.mode === MinionMode.CENTRAL) {
      this.sysCols.push(this.columns.systemMinionColumn);
      this.sysCols.push(this.columns.systemSyncColumn);
    }
  }
}
