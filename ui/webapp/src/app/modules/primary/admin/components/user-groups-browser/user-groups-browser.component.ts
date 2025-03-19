import { Component, inject } from '@angular/core';
import { combineLatest, map } from 'rxjs';
import { BdDataColumn, BdDataGroupingDefinition } from 'src/app/models/data';
import { Permission, UserGroupInfo } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { UserGroupsColumnsService } from 'src/app/modules/core/services/user-groups-columns.service';
import { getGlobalPermission } from 'src/app/modules/core/utils/permission.utils';
import { UserGroupBulkService } from 'src/app/modules/panels/admin/services/user-group-bulk.service';
import { AuthAdminService } from '../../services/auth-admin.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDataGroupingComponent } from '../../../../core/components/bd-data-grouping/bd-data-grouping.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { MatDivider } from '@angular/material/divider';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-user-groups-browser',
    templateUrl: './user-groups-browser.component.html',
    styleUrls: ['./user-groups-browser.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDataGroupingComponent, BdPanelButtonComponent, MatDivider, MatTooltip, BdDialogContentComponent, BdDataDisplayComponent, AsyncPipe]
})
export class UserGroupsBrowserComponent {
  private readonly groupCols = inject(UserGroupsColumnsService);
  protected readonly authAdmin = inject(AuthAdminService);
  protected readonly settings = inject(SettingsService);
  protected readonly bulk = inject(UserGroupBulkService);

  private readonly colPermLevel: BdDataColumn<UserGroupInfo, Permission> = {
    id: 'permLevel',
    name: 'Global Permission',
    data: (r) => getGlobalPermission(r.permissions),
    component: BdDataPermissionLevelCellComponent,
  };

  private readonly colInact: BdDataColumn<UserGroupInfo, string> = {
    id: 'inactive',
    name: 'Inact.',
    data: (r) => (r.inactive ? 'check_box' : 'check_box_outline_blank'),
    component: BdDataIconCellComponent,
    width: '40px',
  };

  protected readonly columns: BdDataColumn<UserGroupInfo, unknown>[] = [
    ...this.groupCols.defaultColumns,
    this.colInact,
    this.colPermLevel,
  ];

  protected loading$ = combineLatest([this.settings.loading$, this.authAdmin.loadingUsers$]).pipe(
    map(([s, a]) => s || a),
  );

  protected getRecordRoute = (row: UserGroupInfo) => {
    return [
      '',
      {
        outlets: { panel: ['panels', 'admin', 'user-group-detail', row.id] },
      },
    ];
  };

  protected grouping: BdDataGroupingDefinition<UserGroupInfo>[] = [
    {
      name: 'Global Permission',
      group: (r) => getGlobalPermission(r.permissions),
      associatedColumn: this.colPermLevel.id,
    },
  ];
}
