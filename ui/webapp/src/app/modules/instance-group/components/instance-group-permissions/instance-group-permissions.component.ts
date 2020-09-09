import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { cloneDeep } from 'lodash';
import { Observable, of } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Permission, ScopedPermission, UserInfo } from 'src/app/models/gen.dtos';
import { UserPickerComponent } from 'src/app/modules/core/components/user-picker/user-picker.component';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { InstanceGroupService } from '../../services/instance-group.service';

@Component({
  selector: 'app-instance-group-permissions',
  templateUrl: './instance-group-permissions.component.html',
  styleUrls: ['./instance-group-permissions.component.css'],
  providers: [SettingsService]
})
export class InstanceGroupPermissionsComponent implements OnInit {

  log: Logger = this.loggingService.getLogger('InstanceGroupPermissionsComponent');

  nameParam: string;

  public INITIAL_SORT_COLUMN = 'name';
  public INITIAL_SORT_DIRECTION = 'asc';

  public loading = true;

  public dataSource: MatTableDataSource<UserInfo> = null;
  private filterPredicate: (d, f) => boolean;
  public userAll: UserInfo[] = [];
  private userTableOri: UserInfo[]; // --> dirty detection

  private _filterValue: string = '';
  get filterValue() {
    return this._filterValue;
  }
  set filterValue(filterValue: string) {
    this._filterValue = filterValue.trim().toLowerCase();
    this.updateFilter();
  }

  private _showGlobal = false;
  get showGlobal(): boolean {
    return this._showGlobal;
  }
  set showGlobal(showGlobal: boolean) {
    this._showGlobal = showGlobal;
    this.updateFilter();
  }

  public displayedColumns: string[] = ['gravatar', 'name', 'fullName', 'email', 'read', 'write', 'admin', 'delete'];

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  constructor(
    private instanceGroupService: InstanceGroupService,
    private dialog: MatDialog,
    private messageBoxService: MessageboxService,
    public settings: SettingsService,
    private loggingService: LoggingService,
    public location: Location,
    private route: ActivatedRoute,
    public routingHistoryService: RoutingHistoryService,
  ) { }

  ngOnInit() {
    this.nameParam = this.route.snapshot.paramMap.get('name');
    this.prepareUsers();
  }

  private prepareUsers(): void {
    this.log.debug('prepareUsers()');
    this.loading = true;
    if (this.dataSource) {
      this.dataSource.data = [];
    }

    this.instanceGroupService.getAllUsers(this.nameParam).subscribe(users => {
      this.userAll = users;

      const userTable: UserInfo[] = [];
      for (const u of users) {
        const cap4instanceGroup: ScopedPermission[] = this.getFilteredPermissions(u);
        if (cap4instanceGroup && cap4instanceGroup.length > 0) {
          const clone = cloneDeep(u);
          clone.permissions = cap4instanceGroup;
          if (this.hasScoped(clone)) {
            // add missing permissions of lower prio (required for grant/revoke actions)
            const hasScopedRead = clone.permissions.find(c => c.scope !== null && c.permission === Permission.READ) != null;
            const hasScopedWrite = clone.permissions.find(c => c.scope !== null && c.permission === Permission.WRITE) != null;
            const hasScopedAdmin = clone.permissions.find(c => c.scope !== null && c.permission === Permission.ADMIN) != null;
            if (hasScopedAdmin && !hasScopedWrite) {
              clone.permissions.push({scope: this.nameParam, permission: Permission.WRITE});
            }
            if ((hasScopedAdmin || hasScopedWrite) && !hasScopedRead) {
              clone.permissions.push({scope: this.nameParam, permission: Permission.READ});
            }
          }
          userTable.push(clone);
        }
      }

      this.userTableOri = cloneDeep(userTable);

      this.dataSource = new MatTableDataSource(userTable);
      this.dataSource.paginator = this.paginator;
      this.dataSource.sort = this.sort;
      this.dataSource.filterPredicate = (data, filter) => {
        return (this.showGlobal || this.hasScoped(data))
          && (this.filterPredicate(data.name, this.filterValue)
          || this.filterPredicate(data.fullName, this.filterValue)
          || this.filterPredicate(data.email, this.filterValue));
      };
      this.updateFilter();
      this.loading = false;
    });
  }

  private updateFilter() {
    try {
      const filterRegex = new RegExp(this._filterValue);
      this.filterPredicate = (d, f) => d && d.toLowerCase().match(f);
    } catch (e) {
      this.filterPredicate = (d, f) => d && d.toLowerCase().includes(f);
    }
    this.dataSource.filter = 'x'; // re-set a dummy value -> triggers update

    if (this.dataSource.paginator) {
      this.dataSource.paginator.firstPage();
    }
  }

  public onAdd() {
    this.dialog.open(UserPickerComponent, {
      width: '500px',
      data: {
        all: this.userAll,
        displayed: this.dataSource.data
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        const clone = cloneDeep(r);
        clone.permissions = this.getFilteredPermissions(clone);
        clone.permissions.push({scope: this.nameParam, permission: Permission.READ});
        this.dataSource.data.push(clone);
        this.dataSource.data = this.dataSource.data; // triggers table update
        this.updateFilter();
      }
    });
  }

  public onGrantWrite(user: UserInfo): void {
    user.permissions.push({scope: this.nameParam, permission: Permission.WRITE});
    const hasScopedRead = user.permissions.find(c => c.scope !== null && c.permission === Permission.READ) != null;
    if (!hasScopedRead) {
      user.permissions.push({scope: this.nameParam, permission: Permission.READ});
    }
    this.updateFilter();
  }

  public onRevokeWrite(user: UserInfo): void {
    user.permissions = user.permissions.filter(c => c.scope === null || c.permission === Permission.READ);
    if (this.hasGlobalRead(user)) {
      this.onDelete(user);
    }
    this.updateFilter();
  }

  public onGrantAdmin(user: UserInfo): void {
    user.permissions.push({scope: this.nameParam, permission: Permission.ADMIN});
    const hasScopedWrite = user.permissions.find(c => c.scope !== null && c.permission === Permission.WRITE) != null;
    if (!hasScopedWrite) {
      this.onGrantWrite(user);
    }
    this.updateFilter();
  }

  public onRevokeAdmin(user: UserInfo): void {
    user.permissions = user.permissions.filter(c => c.scope === null || c.permission !== Permission.ADMIN);
    if (this.hasGlobalWrite(user)) {
      this.onDelete(user);
    }
    this.updateFilter();
  }

  public onDelete(user: UserInfo): void {
    if (this.hasGlobal(user)) {
      // clear scoped
      user.permissions = user.permissions.filter(c => c.scope === null);
    } else {
      // remove user from table
      this.dataSource.data = this.dataSource.data.filter(u => u.name !== user.name);
    }
    this.updateFilter();
  }

  async onDiscardChanges(): Promise<void> {
    const result = await this.messageBoxService.openAsync({
      title: 'Discard changes',
      message: 'Are you sure you want to discard all local changes?',
      mode: MessageBoxMode.QUESTION,
    });

    if (!result) {
      return;
    }
    this.prepareUsers();
  }

  public onSave(): void {
    const result = [];
    this.loading = true;

    // check user in table (new/mod)
    for (const tab of this.dataSource.data) {
      const ori = this.userTableOri.find(u => u.name === tab.name);
      const tabPermission = this.getHighestScopedPermission(tab);
      if (!ori || ori && this.getHighestScopedPermission(ori) !== tabPermission) {
        result.push({user: tab.name, permission: tabPermission});
      }
    }
    // find removed user
    for (const ori of this.userTableOri) {
      const tab = this.dataSource.data.find(u => u.name === ori.name);
      if (!tab) {
        result.push({user: ori.name, permission: null});
      }
    }

    this.instanceGroupService.updateInstanceGroupPermissions(this.nameParam, result)
      .pipe(finalize(() => this.loading = false))
      .subscribe(result => {
        this.prepareUsers();
      });
  }

  public isDirty(): boolean {
    if (this.loading) {
      return false;
    }
    if (this.userTableOri.length !== this.dataSource.data.length) {
      return true;
    }
    for (const ori of this.userTableOri) {
      const tab = this.dataSource.data.find(u => u.name === ori.name);
      if (!tab) {
        return true;
      }
      if (this.getHighestScopedPermission(ori) !== this.getHighestScopedPermission(tab)) {
        return true;
      }
    }
    return false;
  }

  canDeactivate(): Observable<boolean> {
    if (!this.isDirty()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Permissions were modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }


  private getHighestScopedPermission(user: UserInfo): Permission {
    if (this.hasAdmin(user)) {
      return Permission.ADMIN;
    } else if (this.hasWrite(user)) {
      return Permission.WRITE;
    } else if(this.hasRead(user)) {
      return Permission.READ;
    }
    return null;
  }

  private getFilteredPermissions(user: UserInfo): ScopedPermission[] {
    return user.permissions.filter(c => c.scope === null || c.scope === this.nameParam);
  }

  //

  public hasScoped(user: UserInfo): boolean {
    return user.permissions.find(c => c.scope === this.nameParam) != null;
  }

  public hasRead(user: UserInfo): boolean {
    return this.hasScoped(user); // has READ or WRITE or ADMIN
  }

  public hasWrite(user: UserInfo): boolean {
    return user.permissions.find(c => c.scope === this.nameParam && c.permission !== Permission.READ) != null; // -> has WRITE or ADMIN
  }

  public hasAdmin(user: UserInfo): boolean {
    return user.permissions.find(c => c.scope === this.nameParam && c.permission === Permission.ADMIN) != null;
  }

  //

  public hasGlobal(user: UserInfo): boolean {
    return user.permissions.find(c => c.scope === null) != null;
  }

  public hasGlobalRead(user: UserInfo): boolean {
    return this.hasGlobal(user); // -> has global READ or WRITE or ADMIN
  }

  public hasGlobalWrite(user: UserInfo): boolean {
    return user.permissions.find(c => c.scope === null && c.permission !== Permission.READ) != null; // -> has global WRITE or ADMIN
  }

  public hasGlobalAdmin(user: UserInfo): boolean {
    return user.permissions.find(c => c.scope === null && c.permission === Permission.ADMIN) != null;
  }

}
