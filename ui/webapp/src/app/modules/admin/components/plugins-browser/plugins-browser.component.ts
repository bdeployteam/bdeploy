import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { MessageBoxMode } from 'src/app/modules/core/components/messagebox/messagebox.component';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { MessageboxService } from 'src/app/modules/core/services/messagebox.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { FileUploadComponent } from 'src/app/modules/legacy/shared/components/file-upload/file-upload.component';
import { UploadStatus } from 'src/app/modules/legacy/shared/services/upload.service';
import { PluginAdminService } from '../../services/plugin-admin.service';

@Component({
  selector: 'app-plugins-browser',
  templateUrl: './plugins-browser.component.html',
  styleUrls: ['./plugins-browser.component.css'],
  providers: [SettingsService],
})
export class PluginsBrowserComponent implements OnInit, OnDestroy, BdSearchable, AfterViewInit {
  private log: Logger = this.loggingService.getLogger('PluginsBrowserComponent');

  public INITIAL_SORT_COLUMN = 'name';
  public INITIAL_SORT_DIRECTION = 'asc';

  public dataSource: MatTableDataSource<PluginInfoDto> = new MatTableDataSource<PluginInfoDto>([]);
  private filterPredicate: (d, f) => boolean;
  private subscription: Subscription;

  public displayedColumns: string[] = ['global', 'name', 'version', 'editors', 'loaded', 'actions'];

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  loading = true;

  constructor(
    private dialog: MatDialog,
    private messageBoxService: MessageboxService,
    private loggingService: LoggingService,
    private pluginAdminService: PluginAdminService,
    public settings: SettingsService,
    private search: SearchService
  ) {}

  ngOnInit() {
    this.subscription = this.search.register(this);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (data, filter) => {
      return (
        this.filterPredicate(data.name, filter) ||
        this.filterPredicate(data.version, filter) ||
        this.filterPredicate(this.formatEditors(data), filter)
      );
    };

    this.loadPlugins();
  }

  loadPlugins() {
    this.loading = true;
    this.pluginAdminService
      .getAll()
      .pipe(finalize(() => (this.loading = false)))
      .subscribe((plugins) => {
        this.dataSource.data = plugins;
      });
  }

  public bdOnSearch(filterValue: string): void {
    try {
      new RegExp(filterValue.trim().toLowerCase()).compile();
      this.filterPredicate = (d, f) => d && d.toLowerCase().match(f);
    } catch (e) {
      this.filterPredicate = (d, f) => d && d.toLowerCase().includes(f);
    }
    this.dataSource.filter = filterValue.trim().toLowerCase();

    if (this.dataSource.paginator) {
      this.dataSource.paginator.firstPage();
    }
  }

  isLoading() {
    return this.loading || this.settings.isLoading();
  }

  public formatEditors(dto: PluginInfoDto): string {
    return dto.editors.map((ce) => ce.typeName).join(', ');
  }

  public onLoad(dto: PluginInfoDto): void {
    this.loading = true;
    this.pluginAdminService
      .loadGlobalPlugin(dto)
      .pipe(
        catchError((e) => {
          this.loading = false;
          throw e;
        })
      )
      .subscribe((result) => {
        this.loadPlugins();
      });
  }

  public onUnload(dto: PluginInfoDto): void {
    this.loading = true;
    this.pluginAdminService
      .unloadPlugin(dto)
      .pipe(
        catchError((e) => {
          this.loading = false;
          throw e;
        })
      )
      .subscribe((result) => {
        this.loadPlugins();
      });
  }

  public openUploadDialog() {
    const config = new MatDialogConfig();
    config.width = '70%';
    config.height = '75%';
    config.minWidth = '650px';
    config.minHeight = '550px';
    config.data = {
      title: 'Upload Plugins',
      headerMessage:
        'Upload global plugins. The selected archive may either contain a new plugin or a new version of an existing plugin.',
      url: this.pluginAdminService.getGlobalUploadUrl(),
      urlParameter: [{ id: 'replace', name: 'Replace', type: 'boolean' }],
      fileTypes: ['.jar'],
      formDataParam: 'plugin',
      resultDetailsEvaluator: (status: UploadStatus) => {
        if (status.detail == null) {
          return 'Skipping, already loaded from another location!';
        } else {
          return 'Installed and loaded plugin ' + status.detail.name + ' (' + status.detail.version + ')';
        }
      },
    };
    this.dialog
      .open(FileUploadComponent, config)
      .afterClosed()
      .subscribe((e) => {
        this.loadPlugins();
      });
  }

  public onDelete(dto: PluginInfoDto): void {
    this.messageBoxService
      .open({
        title: 'Delete',
        message: 'Do you really want to delete the plugin ' + dto.name + '?',
        mode: MessageBoxMode.CONFIRM,
      })
      .subscribe((r) => {
        if (r) {
          this.loading = true;
          this.pluginAdminService
            .deleteGlobalPlugin(dto)
            .pipe(
              catchError((e) => {
                this.loading = false;
                throw e;
              })
            )
            .subscribe((result) => {
              this.loadPlugins();
            });
        }
      });
  }
}
