import { Component } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { BdDataColumn, BdDataGroupingDefinition } from 'src/app/models/data';
import {
  LDAPSettingsDto,
  Permission,
  SpecialAuthenticators,
  UserInfo,
} from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { UsersColumnsService } from 'src/app/modules/core/services/users-columns.service';
import { AuthAdminService } from '../../services/auth-admin.service';

@Component({
  selector: 'app-users-browser',
  templateUrl: './users-browser.component.html',
  styleUrls: ['./users-browser.component.css'],
})
export class UsersBrowserComponent {
  private colPermLevel: BdDataColumn<UserInfo> = {
    id: 'permLevel',
    name: 'Global Permission',
    data: (r) => this.getGlobalPermission(r),
    component: BdDataPermissionLevelCellComponent,
    width: '100px',
  };

  private colInact: BdDataColumn<UserInfo> = {
    id: 'inactive',
    name: 'Inact.',
    data: (r) => (r.inactive ? 'check_box' : 'check_box_outline_blank'),
    component: BdDataIconCellComponent,
    width: '40px',
  };

  private colAuthBy: BdDataColumn<UserInfo> = {
    id: 'authBy',
    name: 'Authenticated By',
    data: (r) => this.getAuthenticatedBy(r),
    showWhen: '(min-width: 1500px)',
  };

  private colLastLogin: BdDataColumn<UserInfo> = {
    id: 'lastLogin',
    name: 'Last active login',
    data: (r) => r.lastActiveLogin,
    showWhen: '(min-width: 1600px)',
    width: '155px',
    component: BdDataDateCellComponent,
  };

  /* template */ loading$ = combineLatest([
    this.settings.loading$,
    this.authAdmin.loading$,
  ]).pipe(map(([s, a]) => s || a));
  /* template */ columns: BdDataColumn<UserInfo>[] = [
    ...this.userColumns.defaultUsersColumns,
    this.colPermLevel,
    this.colInact,
    this.colAuthBy,
    this.colLastLogin,
  ];
  /* template */ grouping: BdDataGroupingDefinition<UserInfo>[] = [
    {
      name: 'Authenticated By',
      group: (r) => this.getAuthenticatedBy(r),
      associatedColumn: this.colAuthBy.id,
    },
    {
      name: 'Global Permission',
      group: (r) => this.getGlobalPermission(r),
      associatedColumn: this.colPermLevel.id,
    },
  ];
  /* template */ sort: Sort = { active: 'name', direction: 'asc' };

  /* template */ getRecordRoute = (row: UserInfo) => {
    return [
      '',
      { outlets: { panel: ['panels', 'admin', 'user-detail', row.name] } },
    ];
  };

  /* template */ addUser: Partial<UserInfo>;
  /* template */ addConfirm: string;

  constructor(
    public authAdmin: AuthAdminService,
    private userColumns: UsersColumnsService,
    public settings: SettingsService
  ) {}

  public getAuthenticatedBy(userInfo: UserInfo): string {
    if (userInfo.externalSystem) {
      if (userInfo.externalSystem === 'LDAP') {
        const dto: LDAPSettingsDto =
          this.settings.settings$.value.auth.ldapSettings.find(
            (s) => s.id === userInfo.externalTag
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

  public getGlobalPermission(userInfo: UserInfo): Permission {
    let p = userInfo.permissions.find(
      (sc) => sc.scope === null && sc.permission === Permission.ADMIN
    );
    p = p
      ? p
      : userInfo.permissions.find(
          (sc) => sc.scope === null && sc.permission === Permission.WRITE
        );
    p = p
      ? p
      : userInfo.permissions.find(
          (sc) => sc.scope === null && sc.permission === Permission.READ
        );
    p = p
      ? p
      : userInfo.permissions.find(
          (sc) => sc.scope === null && sc.permission === Permission.CLIENT
        );
    return p ? p.permission : null;
  }
}
