import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatTab, MatTabChangeEvent, MatTabGroup } from '@angular/material/tabs';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, combineLatest, finalize, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataSizeCellComponent } from 'src/app/modules/core/components/bd-data-size-cell/bd-data-size-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import {
  constructFilePath,
  decodeFilePath,
  encodeFilePath,
  findFilePath,
  getDescendants,
  toFileList,
} from 'src/app/modules/panels/instances/utils/data-file-utils';
import { ServersService } from '../../../servers/services/servers.service';
import { FilesBulkService } from '../../services/files-bulk.service';
import { FileListEntry, FilePath, FilesService } from '../../services/files.service';
import { InstancesService } from '../../services/instances.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdServerSyncButtonComponent } from '../../../../core/components/bd-server-sync-button/bd-server-sync-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { BdBreadcrumbsComponent } from '../../../../core/components/bd-breadcrumbs/bd-breadcrumbs.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

const colName: BdDataColumn<FilePath, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const colPath: BdDataColumn<FilePath, string> = {
  id: 'path',
  name: 'Path',
  data: (r) => r.path,
};

const colSize: BdDataColumn<FilePath, number> = {
  id: 'size',
  name: 'Size',
  data: (r) => r.size,
  width: '100px',
  showWhen: '(min-width: 700px)',
  component: BdDataSizeCellComponent,
};

const colItems: BdDataColumn<FilePath, string> = {
  id: 'items',
  name: 'Items',
  data: (r) => (!r.children?.length ? '' : r.children.length === 1 ? '1 item' : `${r.children.length} items`),
  width: '100px',
};

const colModTime: BdDataColumn<FilePath, number> = {
  id: 'lastMod',
  name: 'Last Modification',
  data: (r) => r.lastModified,
  width: '155px',
  showWhen: '(min-width: 800px)',
  component: BdDataDateCellComponent,
};

const colAvatar: BdDataColumn<FilePath, string> = {
  id: 'avatar',
  name: '',
  data: (r) => (!r.entry ? 'topic' : 'insert_drive_file'),
  width: '75px',
  component: BdDataIconCellComponent,
};

@Component({
  selector: 'app-files-display',
  templateUrl: './files-display.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdServerSyncButtonComponent,
    MatDivider,
    BdPanelButtonComponent,
    BdButtonComponent,
    BdDialogContentComponent,
    BdNoDataComponent,
    MatTabGroup,
    MatTab,
    BdBreadcrumbsComponent,
    BdDataTableComponent,
    AsyncPipe,
  ],
})
export class FilesDisplayComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly instances = inject(InstancesService);
  private readonly filesService = inject(FilesService);
  private readonly areas = inject(NavAreasService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly searchService = inject(SearchService);
  protected readonly cfg = inject(ConfigService);
  protected readonly servers = inject(ServersService);
  protected readonly authService = inject(AuthenticationService);
  protected readonly filesBulkService = inject(FilesBulkService);

  private searchTerm = '';
  private subscription: Subscription;

  private readonly searchColumns: BdDataColumn<FilePath, unknown>[] = [
    colAvatar,
    colPath,
    colItems,
    colModTime,
    colSize,
  ];
  private readonly defaultColumns: BdDataColumn<FilePath, unknown>[];
  private readonly colDownload: BdDataColumn<FilePath, string> = {
    id: 'download',
    name: 'Downl.',
    data: (r) => `Download ${r.children.length ? 'Folder' : 'File'}`,
    action: (r) => this.doDownload(r),
    icon: () => 'cloud_download',
    width: '50px',
  };
  private readonly colDelete: BdDataColumn<FilePath, string> = {
    id: 'delete',
    name: 'Delete',
    data: () => 'Delete File',
    action: (r) => this.doDelete(r),
    icon: () => 'delete',
    width: '50px',
    actionDisabled: () => !this.authService.isCurrentScopeWrite(),
  };

  protected readonly isDataFiles: boolean;

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<FilePath[]>(null);
  protected noactive$ = new BehaviorSubject<boolean>(true);
  protected remoteDirs$ = new BehaviorSubject<FilePath[]>(null);
  protected tabIndex = -1;
  protected selectedPath: FilePath;
  protected instance: InstanceDto;
  protected columns: BdDataColumn<FilePath, unknown>[];

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  constructor() {
    this.isDataFiles = this.activatedRoute.snapshot.data['isDataFiles'];
    this.defaultColumns = this.isDataFiles
      ? [colAvatar, colName, colItems, colModTime, colSize, this.colDownload, this.colDelete]
      : [colAvatar, colName, colItems, colModTime, colSize, this.colDownload];
  }

  ngOnInit(): void {
    this.subscription = combineLatest([this.instances.current$, this.servers.servers$, this.cfg.isCentral$]).subscribe(
      ([inst, srvs, isCentral]) => {
        if (isCentral && !srvs?.length) {
          return;
        }
        this.load(inst);
      }
    );

    this.subscription.add(
      this.filesService.directories$.subscribe((remoteDirectories) => {
        if (!remoteDirectories) {
          this.remoteDirs$.next(null);
          return;
        }

        const remoteDirs: FilePath[] = [];
        for (const remoteDirectory of remoteDirectories) {
          if (remoteDirectory.problem) {
            console.warn(`Problem reading files from ${remoteDirectory.minion}: ${remoteDirectory.problem}`);
            continue;
          }

          const remoteDirEntries: FileListEntry[] = remoteDirectory.entries.map(
            (entry) => ({ directory: remoteDirectory, entry } as FileListEntry)
          );
          remoteDirs.push(constructFilePath(remoteDirectory.minion, remoteDirEntries, (p) => this.selectPath(p)));
        }
        remoteDirs.sort((filePath) => (filePath.minion === 'master' ? -1 : 0)); // remote directory of master node must be first

        this.remoteDirs$.next(remoteDirs);
        this.loading$.next(false);
      })
    );

    this.subscription.add(
      combineLatest([this.areas.primaryRoute$, this.remoteDirs$]).subscribe(([route, remoteDirs]) => {
        if (!remoteDirs?.length || !route?.params?.['path']) {
          this.selectedPath = null;
          this.records$.next(null);
          return;
        }

        const decodedPathData = decodeFilePath(route.params['path']);
        const remoteDir = remoteDirs.find((filePath) => filePath.minion === decodedPathData.minion);

        // if the path encoded remote directory is not found, select first one (which should belong to the master node)
        if (!remoteDir) {
          this.selectPath(remoteDirs[0]);
          return;
        }

        this.tabIndex = remoteDirs.indexOf(remoteDir);
        this.selectedPath = findFilePath(remoteDir, decodedPathData.path);

        // if selected path could not be determined it means that the current path is empty -> redirect to origin of current node
        if (!this.selectedPath) {
          this.selectPath(remoteDir);
          return;
        }

        this.bdOnSearch(this.searchTerm);
      })
    );

    this.subscription.add(this.searchService.register(this));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  bdOnSearch(value: string): void {
    this.searchTerm = value || '';
    if (this.searchTerm) {
      this.columns = this.searchColumns;
      const filtered = this.selectedPath?.children
        ?.flatMap((child) => getDescendants(child))
        ?.filter((descendant) => descendant.name.includes(this.searchTerm));
      this.records$.next(filtered);
    } else {
      this.columns = this.defaultColumns;
      this.records$.next(this.selectedPath?.children);
    }
  }

  protected load(inst: InstanceDto) {
    this.instance = inst;
    this.noactive$.next(!inst?.activeVersion?.tag);

    if (this.noactive$.value || !this.servers.isSynchronized(inst?.managedServer)) {
      this.loading$.next(false);
      return;
    }

    this.loading$.next(true);
    if (this.isDataFiles) {
      this.filesService.loadDataFiles();
    } else {
      this.filesService.loadLogFiles();
    }
  }

  protected onClick(row: FilePath) {
    if (!row.entry) {
      this.selectPath(row);
    } else {
      this.router.navigate([
        '',
        {
          outlets: {
            panel: ['panels', 'instances', 'files', row.directory.minion, row.entry.path, 'view'],
          },
        },
      ]);
    }
  }

  protected selectPath(path: FilePath) {
    this.router.navigate(['..', encodeFilePath(path)], { relativeTo: this.activatedRoute });
  }

  protected onTabChange(e: MatTabChangeEvent) {
    if (this.tabIndex !== e.index) {
      this.selectPath(this.remoteDirs$.value[e.index]);
    }
  }

  private doDelete(r: FilePath) {
    const dataFiles = r.children.length ? toFileList(r) : [r];
    this.dialog
      .confirm(
        `Delete ${r.path}?`,
        `The ${r.children.length ? 'folder (and its contents)' : 'file'} <strong>${r.path}</strong> on node <strong>${
          r.minion
        }</strong> will be deleted permanently.`,
        'delete'
      )
      .subscribe((confirm) => {
        if (confirm) {
          this.filesBulkService
            .deleteFiles(r.minion, dataFiles)
            .pipe(finalize(() => this.load(this.instance)))
            .subscribe();
        }
      });
  }

  private doDownload(r: FilePath) {
    if (r.children.length) {
      const dataFiles = toFileList(r);
      this.filesBulkService.downloadFiles(dataFiles);
    } else {
      this.instances.download(r.directory, r.entry);
    }
  }
}
