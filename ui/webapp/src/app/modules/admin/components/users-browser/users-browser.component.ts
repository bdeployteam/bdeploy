import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Sort } from '@angular/material/sort';
import { format } from 'date-fns';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn, BdDataGroupingDefinition } from 'src/app/models/data';
import { LDAPSettingsDto, Permission, UserInfo } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataPermissionLevelCellComponent } from 'src/app/modules/core/components/bd-data-permission-level-cell/bd-data-permission-level-cell.component';
import { ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { UsersColumnsService } from 'src/app/modules/core/services/users-columns.service';
import { AuthAdminService } from '../../services/auth-admin.service';

@Component({
  selector: 'app-users-browser',
  templateUrl: './users-browser.component.html',
  styleUrls: ['./users-browser.component.css'],
})
export class UsersBrowserComponent implements OnInit {
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
    data: (r) => (r.lastActiveLogin ? format(r.lastActiveLogin, 'dd.MM.yyyy HH:mm:ss') : 'Never'),
    showWhen: '(min-width: 1600px)',
  };

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('addDialog') private addDialog: TemplateRef<any>;
  @ViewChild('addForm', { static: false }) private addForm: NgForm;

  /* template */ creating$ = new BehaviorSubject<boolean>(false);
  /* template */ loading$ = combineLatest([this.settings.loading$, this.authAdmin.loading$, this.creating$]).pipe(map(([s, a, c]) => s || a || c));
  /* template */ columns: BdDataColumn<UserInfo>[] = [
    ...this.userColumns.defaultUsersColumns,
    this.colPermLevel,
    this.colInact,
    this.colAuthBy,
    this.colLastLogin,
  ];
  /* template */ grouping: BdDataGroupingDefinition<UserInfo>[] = [
    { name: 'Authenticated By', group: (r) => this.getAuthenticatedBy(r) },
    { name: 'Global Permission', group: (r) => this.getGlobalPermission(r) },
  ];
  /* template */ sort: Sort = { active: 'name', direction: 'asc' };

  /* template */ getRecordRoute = (row: UserInfo) => {
    return ['', { outlets: { panel: ['panels', 'admin', 'user-detail', row.name] } }];
  };

  /* template */ addUser: Partial<UserInfo>;
  /* template */ addConfirm: string;

  constructor(public authAdmin: AuthAdminService, private userColumns: UsersColumnsService, public settings: SettingsService) {}

  ngOnInit() {}

  public getAuthenticatedBy(userInfo: UserInfo): string {
    if (userInfo.externalSystem) {
      if (userInfo.externalSystem === 'LDAP') {
        const dto: LDAPSettingsDto = this.settings.settings$.value.auth.ldapSettings.find((s) => s.id === userInfo.externalTag);
        return dto ? dto.description : userInfo.externalTag + ' (not found)';
      } else {
        return userInfo.externalTag; // should not happen
      }
    } else {
      return 'Local user management';
    }
  }

  public getGlobalPermission(userInfo: UserInfo): Permission {
    let p = userInfo.permissions.find((sc) => sc.scope === null && sc.permission === Permission.ADMIN);
    p = p ? p : userInfo.permissions.find((sc) => sc.scope === null && sc.permission === Permission.WRITE);
    p = p ? p : userInfo.permissions.find((sc) => sc.scope === null && sc.permission === Permission.READ);
    return p ? p.permission : null;
  }

  public onAdd(): void {
    this.addUser = {};
    this.addConfirm = '';
    this.dialog
      .message({
        header: 'Add user',
        icon: 'add',
        template: this.addDialog,
        validation: () => !this.addForm || this.addForm.valid,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        if (r) {
          this.creating$.next(true);
          this.authAdmin
            .createLocalUser(this.addUser as UserInfo)
            .pipe(finalize(() => this.creating$.next(false)))
            .subscribe();
        }
      });
  }
}
