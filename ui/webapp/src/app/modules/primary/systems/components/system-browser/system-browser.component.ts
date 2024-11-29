import { Component, OnInit, inject } from '@angular/core';
import { MinionMode, SystemConfigurationDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { SystemsColumnsService } from '../../services/systems-columns.service';
import { SystemsService } from '../../services/systems.service';

@Component({
    selector: 'app-system-browser',
    templateUrl: './system-browser.component.html',
    standalone: false
})
export class SystemBrowserComponent implements OnInit {
  private readonly columns = inject(SystemsColumnsService);
  private readonly config = inject(ConfigService);
  protected readonly systems = inject(SystemsService);
  protected readonly groups = inject(GroupsService);
  protected readonly authService = inject(AuthenticationService);

  protected sysCols = [
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
