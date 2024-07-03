import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { MatTabChangeEvent } from '@angular/material/tabs';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription, combineLatest, finalize } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { CrumbInfo } from 'src/app/modules/core/components/bd-breadcrumbs/bd-breadcrumbs.component';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataSizeCellComponent } from 'src/app/modules/core/components/bd-data-size-cell/bd-data-size-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import {
  constructDataFilePaths,
  decodeDataFilePath,
  encodeDataFilePath,
  findDataFilePath,
  getDescendants,
  toFileList,
} from 'src/app/modules/panels/instances/utils/data-file-utils';
import { ServersService } from '../../../servers/services/servers.service';
import { FilesBulkService } from '../../services/files-bulk.service';
import { FileListEntry, FilePath, FilesService } from '../../services/files.service';
import { InstancesService } from '../../services/instances.service';

const colName: BdDataColumn<FilePath> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const colPath: BdDataColumn<FilePath> = {
  id: 'path',
  name: 'Path',
  data: (r) => r.path,
};

const colSize: BdDataColumn<FilePath> = {
  id: 'size',
  name: 'Size',
  data: (r) => r.size,
  width: '100px',
  showWhen: '(min-width: 700px)',
  component: BdDataSizeCellComponent,
};

const colItems: BdDataColumn<FilePath> = {
  id: 'items',
  name: 'Items',
  data: (r) => (!r.children?.length ? '' : r.children.length === 1 ? '1 item' : `${r.children.length} items`),
  width: '100px',
};

const colModTime: BdDataColumn<FilePath> = {
  id: 'lastMod',
  name: 'Last Modification',
  data: (r) => r.lastModified,
  width: '155px',
  showWhen: '(min-width: 800px)',
  component: BdDataDateCellComponent,
};

const colAvatar: BdDataColumn<FilePath> = {
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

  private readonly searchColumns: BdDataColumn<FilePath>[] = [colAvatar, colPath, colItems, colModTime, colSize];
  private readonly defaultColumns: BdDataColumn<FilePath>[];

  private readonly colDownload: BdDataColumn<FilePath> = {
    id: 'download',
    name: 'Downl.',
    data: (r) => `Download ${r.children.length ? 'Folder' : 'File'}`,
    action: (r) => this.doDownload(r),
    icon: () => 'cloud_download',
    width: '50px',
  };
  private readonly colDelete: BdDataColumn<FilePath> = {
    id: 'delete',
    name: 'Delete',
    data: () => 'Delete File',
    action: (r) => this.doDelete(r),
    icon: () => 'delete',
    width: '50px',
    actionDisabled: () => !this.authService.isCurrentScopeWrite(),
  };

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<FilePath[]>(null);
  protected noactive$ = new BehaviorSubject<boolean>(true);
  protected nodes$ = new BehaviorSubject<FilePath[]>(null);
  protected tabIndex: number = -1;

  protected selectedPath: FilePath;
  protected crumbs: CrumbInfo[] = [];
  protected instance: InstanceDto;
  protected columns: BdDataColumn<FilePath>[];
  protected isDataFiles: boolean;

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
      },
    );

    this.subscription.add(
      this.filesService.directories$.subscribe((dd) => {
        if (!dd) {
          this.nodes$.next(null);
          return;
        }

        const nodes = [];
        for (const dir of dd) {
          const entries: FileListEntry[] = [];
          if (dir.problem) {
            console.warn(`Problem reading files from ${dir.minion}: ${dir.problem}`);
            continue;
          }
          for (const entry of dir.entries) {
            entries.push({ directory: dir, entry });
          }

          const node = constructDataFilePaths(dir.minion, entries, (p) => this.selectPath(p));
          nodes.push(node);
        }

        nodes.sort((a) => (a.minion === 'master' ? -1 : 0)); // master node must be first

        this.nodes$.next(nodes);

        this.loading$.next(false);
      }),
    );

    this.subscription.add(
      combineLatest([this.areas.primaryRoute$, this.nodes$]).subscribe(([route, nodes]) => {
        if (!nodes?.length || !route?.params?.['path']) {
          this.selectedPath = null;
          this.records$.next(null);
          return;
        }
        const path = decodeDataFilePath(route.params['path']);
        const node = nodes.find((filePath) => filePath.minion === path.minion);
        this.tabIndex = nodes.indexOf(node);
        this.selectedPath = findDataFilePath(node, path.path);
        this.bdOnSearch(this.searchTerm);
        // if path encoded node is not found, select first node (which should be master)
        if (!node) {
          this.selectPath(nodes[0]);
        }
      }),
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
      const records = this.selectedPath?.children
        ?.flatMap((child) => getDescendants(child))
        ?.filter((descendant) => descendant.name.indexOf(this.searchTerm) !== -1);
      this.records$.next(records);
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
    this.router.navigate(['..', encodeDataFilePath(path)], { relativeTo: this.activatedRoute });
  }

  protected onTabChange(e: MatTabChangeEvent) {
    if (this.tabIndex !== e.index) {
      this.selectPath(this.nodes$.value[e.index]);
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
        'delete',
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
