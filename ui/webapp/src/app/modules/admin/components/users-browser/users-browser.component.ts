import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { cloneDeep } from 'lodash';
import { Capability, LDAPSettingsDto, UserInfo } from 'src/app/models/gen.dtos';
import { UserEditComponent } from 'src/app/modules/core/components/user-edit/user-edit.component';
import { UserPasswordComponent } from 'src/app/modules/core/components/user-password/user-password.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { AuthAdminService } from '../../services/auth-admin.service';
import { UserGlobalPermissionsComponent } from '../user-global-permissions/user-global-permissions.component';

@Component({
  selector: 'app-users-browser',
  templateUrl: './users-browser.component.html',
  styleUrls: ['./users-browser.component.css'],
  providers: [SettingsService]
})
export class UsersBrowserComponent implements OnInit, AfterViewInit {

  private log: Logger = this.loggingService.getLogger('UsersBrowserComponent');

  public INITIAL_SORT_COLUMN = 'name';
  public INITIAL_SORT_DIRECTION = 'asc';

  public dataSource: MatTableDataSource<UserInfo> = null;
  private filterPredicate: (d, f) => boolean;

  public displayedColumns: string[] = ['gravatar', 'name', 'fullName', 'email', 'globalPermissions', 'inactive', 'authenticatedBy', 'lastActiveLogin', 'actions'];

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  private currentUser: UserInfo;

  constructor(
    private dialog: MatDialog,
    private messageBoxService: MessageboxService,
    private loggingService: LoggingService,
    private authService: AuthenticationService,
    private authAdminService: AuthAdminService,
    public settings: SettingsService
  ) { }

  ngOnInit() {
    this.authService.getUserInfo().subscribe(r => {
      this.currentUser = r;
    });

    this.loadUsers();
  }

  ngAfterViewInit() {
  }

  loadUsers() {
    this.authAdminService.getAll().subscribe(users => {
      if (this.dataSource === null) {
        this.dataSource = new MatTableDataSource(users);
        this.dataSource.paginator = this.paginator;
        this.dataSource.sort = this.sort;

        this.dataSource.filterPredicate = (data, filter) => {
          return this.filterPredicate(data.name, filter)
            || this.filterPredicate(data.fullName, filter)
            || this.filterPredicate(data.email, filter)
            || this.filterPredicate(this.getGlobalPermission(data), filter);
        };
      } else {
        this.dataSource.data = users;
      }
    });
  }

  public applyFilter(filterValue: string): void {
    try {
      const filterRegex = new RegExp(filterValue.trim().toLowerCase());
      this.filterPredicate = (d, f) => d && d.toLowerCase().match(f);
    } catch (e) {
      this.filterPredicate = (d, f) => d && d.toLowerCase().includes(f);
    }
    this.dataSource.filter = filterValue.trim().toLowerCase();

    if (this.dataSource.paginator) {
      this.dataSource.paginator.firstPage();
    }
  }

  public getAuthenticatedBy(userInfo: UserInfo): string {
    if(userInfo.externalSystem) {
      if (userInfo.externalSystem === 'LDAP') {
        const dto: LDAPSettingsDto = this.settings.getSettings().auth.ldapSettings.find(s => s.server === userInfo.externalTag);
        return dto ? dto.description : userInfo.externalTag + ' (not found)';
      } else {
        return userInfo.externalTag; // should not happen
      }
    } else {
      return 'Local user management';
    }
  }

  public getGlobalPermission(userInfo: UserInfo): Capability {
    let p = userInfo.capabilities.find(sc => sc.scope === null && sc.capability === Capability.ADMIN);
    p = p ? p : userInfo.capabilities.find(sc => sc.scope === null && sc.capability === Capability.WRITE);
    p = p ? p : userInfo.capabilities.find(sc => sc.scope === null && sc.capability === Capability.READ);
    return p ? p.capability : null;
  }

  public isCurrentUser(userInfo: UserInfo): boolean {
    return this.currentUser && userInfo.name === this.currentUser.name;
  }

  public onAdd(): void {
    this.dialog.open(UserEditComponent, {
      width: '500px',
      data: null,
    }).afterClosed().subscribe(r => {
      if (r) {
        this.authAdminService.createLocalUser(r).subscribe(result => {
          this.loadUsers();
        });
      }
    });
  }

  public onEdit(userInfo: UserInfo): void {
    this.dialog.open(UserEditComponent, {
      width: '500px',
      data: cloneDeep(userInfo),
    }).afterClosed().subscribe(r => {
      if (r) {
        this.authAdminService.updateUser(r).subscribe(result => {
          this.loadUsers();
        });
      }
    });
  }

  public onSetPassword(userInfo: UserInfo): void {
    this.dialog.open(UserPasswordComponent, {
      width: '500px',
      data: {
        isAdmin: true,
        user: userInfo.name
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        this.authAdminService.updateLocalUserPassword(r.user, r.newPassword).subscribe(result => {
          this.loadUsers();
        });
      }
    });
  }

  public onGlobalPermissions(userInfo: UserInfo): void {
    this.dialog.open(UserGlobalPermissionsComponent, {
      width: '500px',
      data: cloneDeep(userInfo),
    }).afterClosed().subscribe(r => {
      if (r) {
        this.authAdminService.updateUser(r).subscribe(result => {
          this.loadUsers();
        });
      }
    });
  }

  public onSetInactive(userInfo: UserInfo, newValue: boolean): void {
    if (userInfo.inactive === newValue) {
      this.log.warn('user ' + userInfo.name + ' is already ' + (newValue ? 'active' : 'inactive') + '!');
      this.loadUsers();
      return;
    }
    userInfo.inactive = newValue;
    this.authAdminService.updateUser(userInfo).subscribe(result => {
      this.loadUsers();
    });
  }

  public onDelete(userInfo: UserInfo): void {
    this.messageBoxService.open({
      title: 'Delete',
      message: 'Do you really want to delete user ' + userInfo.name + '?',
      mode: MessageBoxMode.CONFIRM,
    }).subscribe(r => {
      if (r) {
        this.authAdminService.deleteUser(userInfo.name).subscribe(result => {
          this.loadUsers();
        });
      }
    });
  }

}
