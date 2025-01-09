import { Component, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { BdDataColumn, BdDataGroupingDefinition } from 'src/app/models/data';
import { LDAPSettingsDto, Permission, SpecialAuthenticators, UserGroupInfo, UserInfo } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { UsersColumnsService } from 'src/app/modules/core/services/users-columns.service';
import { getGlobalPermission } from 'src/app/modules/core/utils/permission.utils';
import { UserBulkService } from 'src/app/modules/panels/admin/services/user-bulk.service';
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
    selector: 'app-users-browser',
    templateUrl: './users-browser.component.html',
    styleUrls: ['./users-browser.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDataGroupingComponent, BdPanelButtonComponent, MatDivider, MatTooltip, BdDialogContentComponent, BdDataDisplayComponent, AsyncPipe]
})
export class UsersBrowserComponent {
  private readonly userColumns = inject(UsersColumnsService);
  protected readonly authAdmin = inject(AuthAdminService);
  protected readonly settings = inject(SettingsService);
  protected readonly bulk = inject(UserBulkService);

  private readonly colInGroups: BdDataColumn<UserInfo> = {
    id: 'inGroups',
    name: 'In Groups',
    data: (r) => this.getInGroups(r),
  };

  private readonly colPermLevel: BdDataColumn<UserInfo> = {
    id: 'permLevel',
    name: 'Global Permission',
    data: (r) => getGlobalPermission(r.permissions),
    component: BdDataPermissionLevelCellComponent,
    width: '100px',
  };

  private readonly colInheritedPermLevel: BdDataColumn<UserInfo> = {
    id: 'inheritedPermLevel',
    name: 'Inherited Global Permission',
    data: (r) => this.getInheritedGlobalPermission(r),
    component: BdDataPermissionLevelCellComponent,
    width: '100px',
  };

  private readonly colInact: BdDataColumn<UserInfo> = {
    id: 'inactive',
    name: 'Inact.',
    data: (r) => (r.inactive ? 'check_box' : 'check_box_outline_blank'),
    component: BdDataIconCellComponent,
    width: '40px',
  };

  private readonly colAuthBy: BdDataColumn<UserInfo> = {
    id: 'authBy',
    name: 'Authenticated By',
    data: (r) => this.getAuthenticatedBy(r),
    showWhen: '(min-width: 1500px)',
  };

  private readonly colLastLogin: BdDataColumn<UserInfo> = {
    id: 'lastLogin',
    name: 'Last active login',
    data: (r) => r.lastActiveLogin,
    showWhen: '(min-width: 1600px)',
    width: '155px',
    component: BdDataDateCellComponent,
  };

  protected loading$ = combineLatest([this.settings.loading$, this.authAdmin.loadingUsers$]).pipe(
    map(([s, a]) => s || a),
  );
  protected readonly columns: BdDataColumn<UserInfo>[] = [
    ...this.userColumns.defaultUsersColumns,
    this.colPermLevel,
    this.colInGroups,
    this.colInheritedPermLevel,
    this.colInact,
    this.colAuthBy,
    this.colLastLogin,
  ];
  protected grouping: BdDataGroupingDefinition<UserInfo>[] = [
    {
      name: 'Authenticated By',
      group: (r) => this.getAuthenticatedBy(r),
      associatedColumn: this.colAuthBy.id,
    },
    {
      name: 'Global Permission',
      group: (r) => getGlobalPermission(r.permissions),
      associatedColumn: this.colPermLevel.id,
    },
  ];
  protected sort: Sort = { active: 'name', direction: 'asc' };

  protected getRecordRoute = (row: UserInfo) => [
    '',
    { outlets: { panel: ['panels', 'admin', 'user-detail', row.name] } },
  ];

  protected addUser: Partial<UserInfo>;
  protected addConfirm: string;

  private getGroups(userInfo: UserInfo): UserGroupInfo[] {
    return this.authAdmin.userGroups$.value?.filter((g) => userInfo.groups.some((gid) => gid === g.id)) || [];
  }

  private getInGroups(userInfo: UserInfo): string {
    return this.getGroups(userInfo)
      .map((g) => g.name)
      .join(', ');
  }

  private getInheritedGlobalPermission(userInfo: UserInfo): Permission {
    const groupPermissions = this.getGroups(userInfo).flatMap((g) => g.permissions);
    return getGlobalPermission(groupPermissions);
  }

  private getAuthenticatedBy(userInfo: UserInfo): string {
    if (userInfo.externalSystem) {
      if (userInfo.externalSystem === 'LDAP') {
        const dto: LDAPSettingsDto = this.settings.settings$.value.auth.ldapSettings.find(
          (s) => s.id === userInfo.externalTag,
        );
        return dto ? dto.description : 'All configured Servers';
      } else if (userInfo.externalSystem === 'OIDC') {
        return 'OpenID Connect';
      } else if (userInfo.externalSystem === SpecialAuthenticators.AUTH0) {
        return 'Auth0';
      } else if (userInfo.externalSystem === SpecialAuthenticators.OKTA) {
        return 'Okta';
      } else {
        return userInfo.externalTag; // should not happen
      }
    } else {
      return 'Local user management';
    }
  }
}
