import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, ViewContainerRef } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTable, MatTableDataSource } from '@angular/material/table';
import { MatTabChangeEvent } from '@angular/material/tabs';
import { Base64 } from 'js-base64';
import { Observable, of, Subscription } from 'rxjs';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { RoutingHistoryService } from 'src/app/modules/legacy/core/services/routing-history.service';
import { RemoteDirectory, RemoteDirectoryEntry, StringEntryChunkDto } from '../../../../models/gen.dtos';
import { LoggingAdminService } from '../../services/logging-admin.service';

@Component({
  selector: 'app-log-files-browser',
  templateUrl: './log-files-browser.component.html',
  styleUrls: ['./log-files-browser.component.css'],
})
export class LogFilesBrowserComponent implements OnInit, OnDestroy, BdSearchable {
  public INITIAL_PAGE_SIZE = 10;
  public INITIAL_PAGE_INDEX = 0;
  public INITIAL_SORT_COLUMN = 'lastModified';
  public INITIAL_SORT_DIRECTION = 'desc';

  pageSize = this.INITIAL_PAGE_SIZE;

  @ViewChild(MatTable)
  table: MatTable<RemoteDirectoryEntry>;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  public configContent = '';

  public displayedColumns: string[] = ['icon', 'path', 'size', 'lastModified', 'download'];

  public logDirectories: RemoteDirectory[];
  public dataSource: MatTableDataSource<RemoteDirectoryEntry>;
  public filterRegex: RegExp;
  public activeTabIndex = 0;

  public activeRemoteDirectory: RemoteDirectory = null;
  public activeRemoteDirectoryEntry: RemoteDirectoryEntry = null;

  private overlayRef: OverlayRef;
  private subscription: Subscription;

  constructor(
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    public location: Location,
    public routingHistoryService: RoutingHistoryService,
    public authService: AuthenticationService,
    private loggingAdmin: LoggingAdminService,
    private dialog: MatDialog,
    private search: SearchService
  ) {}

  public ngOnInit(): void {
    this.reload();
    this.subscription = this.search.register(this);
  }

  public ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public reload() {
    this.loggingAdmin.listLogDirs().subscribe((logDirs) => {
      this.logDirectories = logDirs.sort((a, b) => {
        if (a.minion === 'master') {
          return -1;
        } else if (b.minion === 'master') {
          return 1;
        } else {
          return a.minion.toLocaleLowerCase().localeCompare(b.minion.toLocaleLowerCase());
        }
      });
      // Enqueue an update of the data source as soon as the tabs and the content is newly created
      setTimeout(() => this.updateDataSource(), 0);
    });
  }

  public formatSize(size: number): string {
    const i: number = size === 0 ? 0 : Math.min(4, Math.floor(Math.log(size) / Math.log(1024)));
    return (i === 0 ? size : (size / Math.pow(1024, i)).toFixed(2)) + ' ' + ['B', 'kB', 'MB', 'GB', 'TB'][i];
  }

  public formatLastModified(lastModified: number): string {
    return new Date(lastModified).toLocaleString();
  }

  public download(remoteDirectory: RemoteDirectory, remoteDirectoryEntry: RemoteDirectoryEntry) {
    this.loggingAdmin.downloadLogFileContent(remoteDirectory, remoteDirectoryEntry);
  }

  updateDataSource() {
    // Prepare data-source with sorting and paging
    if (this.dataSource == null) {
      this.dataSource = new MatTableDataSource();
    }
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (data, filter) => {
      const fileName = data.path.toLowerCase();
      const pattern = filter.trim().toLowerCase();
      // Prefer regex if we have a valid one
      if (this.filterRegex) {
        const match = fileName.match(pattern) != null;
        return match;
      }
      return fileName.includes(pattern);
    };
    this.dataSource.paginator = this.paginator;

    // Pass list of entires to the table
    const selectedDir = this.logDirectories[this.activeTabIndex];
    this.dataSource.data = selectedDir.entries;
  }

  onTabChanged(event: MatTabChangeEvent) {
    this.activeTabIndex = event.index;
    this.updateDataSource();
  }

  bdOnSearch(filterValue: string) {
    try {
      this.filterRegex = new RegExp(filterValue.toLowerCase());
    } catch (e) {
      this.filterRegex = null;
    }
    this.dataSource.filter = filterValue;
  }

  getCurrentOutputEntryFetcher(): () => Observable<RemoteDirectoryEntry> {
    return () => of(this.activeRemoteDirectoryEntry ? this.activeRemoteDirectoryEntry : null);
  }

  getOutputContentFetcher(): (offset: number, limit: number) => Observable<StringEntryChunkDto> {
    return (offset, limit) => {
      return this.loggingAdmin.getLogContentChunk(
        this.activeRemoteDirectory,
        this.activeRemoteDirectoryEntry,
        offset,
        limit,
        true
      );
    };
  }

  getContentDownloader(): () => void {
    return () => this.loggingAdmin.downloadLogFileContent(this.activeRemoteDirectory, this.activeRemoteDirectoryEntry);
  }

  openOutputOverlay(
    remoteDirectory: RemoteDirectory,
    remoteDirectoryEntry: RemoteDirectoryEntry,
    template: TemplateRef<any>
  ) {
    this.activeRemoteDirectory = remoteDirectory;
    this.activeRemoteDirectoryEntry = remoteDirectoryEntry;

    this.closeOutputOverlay();

    this.overlayRef = this.overlay.create({
      height: '90%',
      width: '90%',
      positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
      hasBackdrop: true,
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOutputOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  closeOutputOverlay() {
    if (this.overlayRef) {
      this.activeRemoteDirectory = null;
      this.activeRemoteDirectoryEntry = null;
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

  openConfigEditor(template: TemplateRef<any>) {
    this.loggingAdmin.getLogConfig().subscribe((cfg) => {
      this.configContent = Base64.decode(cfg);

      const config = new MatDialogConfig();
      config.width = '70%';
      config.minWidth = '650px';

      this.dialog
        .open(template, config)
        .afterClosed()
        .subscribe((apply) => {
          if (apply) {
            this.loggingAdmin.setLogConfig(Base64.encode(this.configContent)).subscribe((_) => {});
          }
        });
    });
  }
}
