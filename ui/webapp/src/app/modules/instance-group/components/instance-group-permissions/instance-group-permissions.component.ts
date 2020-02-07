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
import { Capability, ScopedCapability, UserInfo } from 'src/app/models/gen.dtos';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { InstanceGroupService } from '../../services/instance-group.service';
import { InstanceGroupPermissionsAddComponent } from '../instance-group-permissions-add/instance-group-permissions-add.component';

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
  public userAll: UserInfo[] = [];
  private userTableOri: UserInfo[]; // --> dirty detection

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
    private route: ActivatedRoute
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
        const cap4instanceGroup: ScopedCapability[] = this.getFilteredCapabilities(u);
        if (cap4instanceGroup && cap4instanceGroup.length > 0) {
          const clone = cloneDeep(u);
          clone.capabilities = cap4instanceGroup;
          if (this.hasScoped(clone)) {
            // add missing capabilities of lower prio (required for grant/revoke actions)
            const hasScopedRead = clone.capabilities.find(c => c.scope !== null && c.capability === Capability.READ) != null;
            const hasScopedWrite = clone.capabilities.find(c => c.scope !== null && c.capability === Capability.WRITE) != null;
            const hasScopedAdmin = clone.capabilities.find(c => c.scope !== null && c.capability === Capability.ADMIN) != null;
            if (hasScopedAdmin && !hasScopedWrite) {
              clone.capabilities.push({scope: this.nameParam, capability: Capability.WRITE});
            }
            if ((hasScopedAdmin || hasScopedWrite) && !hasScopedRead) {
              clone.capabilities.push({scope: this.nameParam, capability: Capability.READ});
            }
          }
          userTable.push(clone);
        }
      }

      this.userTableOri = cloneDeep(userTable);

      this.dataSource = new MatTableDataSource(userTable);
      this.dataSource.paginator = this.paginator;
      this.dataSource.sort = this.sort;

      this.loading = false;
    });
  }

  public onAdd() {
    this.dialog.open(InstanceGroupPermissionsAddComponent, {
      width: '500px',
      data: {
        all: this.userAll,
        displayed: this.dataSource.data
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        const clone = cloneDeep(r);
        clone.capabilities = this.getFilteredCapabilities(clone);
        clone.capabilities.push({scope: this.nameParam, capability: Capability.READ});
        this.dataSource.data.push(clone);
        this.dataSource.data = this.dataSource.data; // triggers table update
      }
    });
  }

  public onGrantWrite(user: UserInfo): void {
    user.capabilities.push({scope: this.nameParam, capability: Capability.WRITE});
    const hasScopedRead = user.capabilities.find(c => c.scope !== null && c.capability === Capability.READ) != null;
    if (!hasScopedRead) {
      user.capabilities.push({scope: this.nameParam, capability: Capability.READ});
    }
  }

  public onRevokeWrite(user: UserInfo): void {
    user.capabilities = user.capabilities.filter(c => c.scope === null || c.capability === Capability.READ);
  }

  public onGrantAdmin(user: UserInfo): void {
    user.capabilities.push({scope: this.nameParam, capability: Capability.ADMIN});
    const hasScopedWrite = user.capabilities.find(c => c.scope !== null && c.capability === Capability.WRITE) != null;
    if (!hasScopedWrite) {
      this.onGrantWrite(user);
    }
  }

  public onRevokeAdmin(user: UserInfo): void {
    user.capabilities = user.capabilities.filter(c => c.scope === null || c.capability !== Capability.ADMIN);
  }

  public onDelete(user: UserInfo): void {
    if (this.hasGlobal(user)) {
      // clear scoped
      user.capabilities = user.capabilities.filter(c => c.scope === null);
    } else {
      // remove user from table
      this.dataSource.data = this.dataSource.data.filter(u => u.name !== user.name);
    }
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
      const tabCapability = this.getHighestScopedCapability(tab);
      if (!ori || ori && this.getHighestScopedCapability(ori) !== tabCapability) {
        result.push({user: tab.name, capability: tabCapability});
      }
    }
    // find removed user
    for (const ori of this.userTableOri) {
      const tab = this.dataSource.data.find(u => u.name === ori.name);
      if (!tab) {
        result.push({user: ori.name, capability: null});
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
      if (this.getHighestScopedCapability(ori) !== this.getHighestScopedCapability(tab)) {
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


  private getHighestScopedCapability(user: UserInfo): Capability {
    if (this.hasAdmin(user)) {
      return Capability.ADMIN;
    } else if (this.hasWrite(user)) {
      return Capability.WRITE;
    } else if(this.hasRead(user)) {
      return Capability.READ;
    }
    return null;
  }

  private getFilteredCapabilities(user: UserInfo): ScopedCapability[] {
    return user.capabilities.filter(c => c.scope === null || c.scope === this.nameParam);
  }

  //

  public hasScoped(user: UserInfo): boolean {
    return user.capabilities.find(c => c.scope === this.nameParam) != null;
  }

  public hasRead(user: UserInfo): boolean {
    return this.hasScoped(user); // has READ or WRITE or ADMIN
  }

  public hasWrite(user: UserInfo): boolean {
    return user.capabilities.find(c => c.scope === this.nameParam && c.capability !== Capability.READ) != null; // -> has WRITE or ADMIN
  }

  public hasAdmin(user: UserInfo): boolean {
    return user.capabilities.find(c => c.scope === this.nameParam && c.capability === Capability.ADMIN) != null;
  }

  //

  public hasGlobal(user: UserInfo): boolean {
    return user.capabilities.find(c => c.scope === null) != null;
  }

  public hasGlobalRead(user: UserInfo): boolean {
    return this.hasGlobal(user); // -> has global READ or WRITE or ADMIN
  }

  public hasGlobalWrite(user: UserInfo): boolean {
    return user.capabilities.find(c => c.scope === null && c.capability !== Capability.READ) != null; // -> has global WRITE or ADMIN
  }

  public hasGlobalAdmin(user: UserInfo): boolean {
    return user.capabilities.find(c => c.scope === null && c.capability === Capability.ADMIN) != null;
  }

}
