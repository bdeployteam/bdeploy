import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewChild, ViewContainerRef } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTable, MatTableDataSource } from '@angular/material/table';
import { MatTabChangeEvent } from '@angular/material/tabs';
import { ActivatedRoute } from '@angular/router';
import { Observable, of } from 'rxjs';
import { InstanceConfiguration, InstanceDirectory, InstanceDirectoryEntry, StringEntryChunkDto } from '../../../../models/gen.dtos';
import { DownloadService } from '../../../shared/services/download.service';
import { InstanceService } from '../../services/instance.service';

@Component({
  selector: 'app-data-files-browser',
  templateUrl: './data-files-browser.component.html',
  styleUrls: ['./data-files-browser.component.css'],
})
export class DataFilesBrowserComponent implements OnInit {
  public INITIAL_PAGE_SIZE = 10;
  public INITIAL_PAGE_INDEX = 0;
  public INITIAL_SORT_COLUMN = 'lastModified';
  public INITIAL_SORT_DIRECTION = 'desc';

  groupParam: string = this.route.snapshot.paramMap.get('group');
  uuidParam: string = this.route.snapshot.paramMap.get('uuid');
  versionParam: string = this.route.snapshot.paramMap.get('version');

  @ViewChild(MatTable)
  table: MatTable<InstanceDirectoryEntry>;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild(MatSort)
  sort: MatSort;

  public displayedColumns: string[] = ['icon', 'path', 'size', 'lastModified', 'download'];

  public instanceVersion: InstanceConfiguration;
  public instanceDirectories: InstanceDirectory[];
  public dataSource: MatTableDataSource<InstanceDirectoryEntry>;
  public filterRegex: RegExp;
  public activeTabIndex = 0;

  public activeInstanceDirectory: InstanceDirectory = null;
  public activeInstanceDirectoryEntry: InstanceDirectoryEntry = null;

  private overlayRef: OverlayRef;

  constructor(
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    public location: Location,
    private dlService: DownloadService,
  ) {}

  public ngOnInit(): void {
    this.instanceService
      .getInstanceVersion(this.groupParam, this.uuidParam, this.versionParam)
      .subscribe(instanceVersion => {
        this.instanceVersion = instanceVersion;
      });
    this.reload();
  }

  public reload() {
    this.instanceService.listDataDirSnapshot(this.groupParam, this.uuidParam).subscribe(instanceDirectories => {
      this.instanceDirectories = instanceDirectories.sort((a, b) => {
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

  public download(instanceDirectory: InstanceDirectory, instanceDirectoryEntry: InstanceDirectoryEntry) {
    this.instanceService
      .downloadDataFileContent(this.groupParam, this.uuidParam, instanceDirectory, instanceDirectoryEntry);
  }

  private downloadFile(filename: string, data: string): void {
    const blob = new Blob([data], { type: 'text/plain' });
    this.dlService.downloadBlob(filename, blob);
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
    const selectedDir = this.instanceDirectories[this.activeTabIndex];
    this.dataSource.data = selectedDir.entries;
  }

  onTabChanged(event: MatTabChangeEvent) {
    this.activeTabIndex = event.index;
    this.updateDataSource();
  }

  applyFilter(filterValue: string) {
    try {
      this.filterRegex = new RegExp(filterValue.toLowerCase());
    } catch (e) {
      this.filterRegex = null;
    }
    this.dataSource.filter = filterValue;
  }

  getCurrentOutputEntryFetcher(): () => Observable<InstanceDirectoryEntry> {
    return () => of(this.activeInstanceDirectoryEntry ? this.activeInstanceDirectoryEntry : null);
  }

  getOutputContentFetcher(): (offset: number, limit: number) => Observable<StringEntryChunkDto> {
    return (offset, limit) => {
      return this.instanceService.getContentChunk(
        this.groupParam,
        this.uuidParam,
        this.activeInstanceDirectory,
        this.activeInstanceDirectoryEntry,
        offset,
        limit,
        true,
      );
    };
  }

  openOutputOverlay(
    instanceDirectory: InstanceDirectory,
    instanceDirectoryEntry: InstanceDirectoryEntry,
    template: TemplateRef<any>,
  ) {
    this.activeInstanceDirectory = instanceDirectory;
    this.activeInstanceDirectoryEntry = instanceDirectoryEntry;

    this.closeOutputOverlay();

    this.overlayRef = this.overlay.create({
      height: '90%',
      width: '90%',
      positionStrategy: this.overlay
        .position()
        .global()
        .centerHorizontally()
        .centerVertically(),
      hasBackdrop: true,
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOutputOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  closeOutputOverlay() {
    if (this.overlayRef) {
      this.activeInstanceDirectory = null;
      this.activeInstanceDirectoryEntry = null;
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }
}
