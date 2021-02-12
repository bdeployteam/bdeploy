import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort, SortDirection } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { cloneDeep } from 'lodash-es';
import { Observable, of, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Permission, ScopedPermission, UserInfo } from 'src/app/models/gen.dtos';
import { MessageBoxMode } from 'src/app/modules/core/components/messagebox/messagebox.component';
import { UserPickerComponent } from 'src/app/modules/core/components/user-picker/user-picker.component';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { MessageboxService } from 'src/app/modules/core/services/messagebox.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { RoutingHistoryService } from 'src/app/modules/legacy/core/services/routing-history.service';
import { SoftwareRepositoryService } from '../../services/software-repository.service';

@Component({
  selector: 'app-software-repository-permissions',
  templateUrl: './software-repository-permissions.component.html',
  styleUrls: ['./software-repository-permissions.component.css'],
  providers: [SettingsService],
})
export class SoftwareRepositoryPermissionsComponent implements OnInit, OnDestroy, BdSearchable {
  log: Logger = this.loggingService.getLogger('SoftwareRepositoryPermissionsComponent');

  nameParam: string;

  public INITIAL_SORT_COLUMN = 'name';
  public INITIAL_SORT_DIRECTION: SortDirection = 'asc';

  public loading = true;

  public dataSource: MatTableDataSource<UserInfo> = null;
  private filterPredicate: (d, f) => boolean;
  public userAll: UserInfo[] = [];
  private userTableOri: UserInfo[]; // --> dirty detection

  private _filterValue = '';
  private subscription: Subscription;

  private _showGlobal = false;
  get showGlobal(): boolean {
    return this._showGlobal;
  }
  set showGlobal(showGlobal: boolean) {
    this._showGlobal = showGlobal;
    this.updateFilter();
  }

  public displayedColumns: string[] = ['gravatar', 'name', 'fullName', 'email', 'write', 'delete'];

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  constructor(
    private softwareRepositoryService: SoftwareRepositoryService,
    private dialog: MatDialog,
    private messageBoxService: MessageboxService,
    public settings: SettingsService,
    private loggingService: LoggingService,
    public location: Location,
    private route: ActivatedRoute,
    public routingHistoryService: RoutingHistoryService,
    private search: SearchService
  ) {}

  ngOnInit() {
    this.nameParam = this.route.snapshot.paramMap.get('name');
    this.prepareUsers();

    this.subscription = this.search.register(this);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  bdOnSearch(filterValue: string) {
    this._filterValue = filterValue.trim().toLowerCase();
    this.updateFilter();
  }

  private prepareUsers(): void {
    this.log.debug('prepareUsers()');
    this.loading = true;
    if (this.dataSource) {
      this.dataSource.data = [];
    }

    this.softwareRepositoryService.getAllUsers(this.nameParam).subscribe((users) => {
      this.userAll = users;

      const userTable: UserInfo[] = [];
      for (const u of users) {
        const cap4repo: ScopedPermission[] = this.getFilteredPermissions(u);
        if (cap4repo && cap4repo.length > 0) {
          const clone = cloneDeep(u);
          clone.permissions = cap4repo;
          userTable.push(clone);
        }
      }

      this.userTableOri = cloneDeep(userTable);
      this.dataSource = new MatTableDataSource(userTable);
      this.dataSource.paginator = this.paginator;
      this.dataSource.sort = this.sort;
      this.dataSource.filterPredicate = (data, filter) => {
        return (
          (this.showGlobal || this.hasScoped(data)) &&
          (this.filterPredicate(data.name, this._filterValue) ||
            this.filterPredicate(data.fullName, this._filterValue) ||
            this.filterPredicate(data.email, this._filterValue))
        );
      };
      this.updateFilter();
      this.loading = false;
    });
  }

  private updateFilter() {
    try {
      new RegExp(this._filterValue).compile();
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
    this.dialog
      .open(UserPickerComponent, {
        width: '500px',
        data: {
          all: this.userAll,
          displayed: this.dataSource.data,
        },
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          const clone = cloneDeep(r);
          clone.permissions = this.getFilteredPermissions(clone);
          clone.permissions.push({
            scope: this.nameParam,
            permission: Permission.WRITE,
          });
          this.dataSource.data.push(clone);
          this.dataSource.data = this.dataSource.data; // triggers table update
          this.updateFilter();
        }
      });
  }

  public onDelete(user: UserInfo): void {
    this.dataSource.data = this.dataSource.data.filter((u) => u.name !== user.name);
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

    // check user in table (added)
    for (const tab of this.dataSource.data) {
      const ori = this.userTableOri.find((u) => u.name === tab.name);
      if (!ori) {
        result.push({ user: tab.name, permission: Permission.WRITE });
      }
    }
    // find removed user
    for (const ori of this.userTableOri) {
      const tab = this.dataSource.data.find((u) => u.name === ori.name);
      if (!tab) {
        result.push({ user: ori.name, permission: null });
      }
    }

    this.softwareRepositoryService
      .updateSoftwareRepositoryPermissions(this.nameParam, result)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe((_) => {
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
      const tab = this.dataSource.data.find((u) => u.name === ori.name);
      if (!tab) {
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

  private getFilteredPermissions(user: UserInfo): ScopedPermission[] {
    // scoped WRITE or global WRITE/ADMIN
    return user.permissions.filter(
      (c) =>
        (c.scope === this.nameParam && c.permission === Permission.WRITE) ||
        (c.scope === null && c.permission !== Permission.READ)
    );
  }

  public hasScoped(user: UserInfo): boolean {
    return user.permissions.find((c) => c.scope === this.nameParam) != null;
  }

  public hasGlobalWrite(user: UserInfo): boolean {
    return user.permissions.find((c) => c.scope === null && c.permission !== Permission.READ) != null; // -> has global WRITE or ADMIN
  }
}
