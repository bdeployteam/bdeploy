import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatTabChangeEvent } from '@angular/material/tabs';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { CrumbInfo } from 'src/app/modules/core/components/bd-breadcrumbs/bd-breadcrumbs.component';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataSizeCellComponent } from 'src/app/modules/core/components/bd-data-size-cell/bd-data-size-cell.component';
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
import { DataFilesBulkService } from '../../services/data-files-bulk.service';
import { InstancesService } from '../../services/instances.service';
import { FileListEntry, LogDataPath, LogDataService } from '../../services/log-data.service';

const colName: BdDataColumn<LogDataPath> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const colPath: BdDataColumn<LogDataPath> = {
  id: 'path',
  name: 'Path',
  data: (r) => r.path,
};

const colSize: BdDataColumn<LogDataPath> = {
  id: 'size',
  name: 'Size',
  data: (r) => r.size,
  width: '100px',
  showWhen: '(min-width: 700px)',
  component: BdDataSizeCellComponent,
};

const colItems: BdDataColumn<LogDataPath> = {
  id: 'items',
  name: 'Items',
  data: (r) => (!r.children?.length ? '' : r.children.length === 1 ? '1 item' : `${r.children.length} items`),
  width: '100px',
};

const colModTime: BdDataColumn<LogDataPath> = {
  id: 'lastMod',
  name: 'Last Modification',
  data: (r) => r.lastModified,
  width: '155px',
  showWhen: '(min-width: 800px)',
  component: BdDataDateCellComponent,
};

const colAvatar: BdDataColumn<LogDataPath> = {
  id: 'avatar',
  name: '',
  data: (r) => (!r.entry ? 'topic' : 'insert_drive_file'),
  width: '75px',
  component: BdDataIconCellComponent,
};

@Component({
  selector: 'app-data-files',
  templateUrl: './log-data.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogDataComponent implements OnInit, OnDestroy, BdSearchable {
  private instances = inject(InstancesService);
  private lds = inject(LogDataService);
  private areas = inject(NavAreasService);
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  private searchService = inject(SearchService);
  protected cfg = inject(ConfigService);
  protected servers = inject(ServersService);
  protected authService = inject(AuthenticationService);
  protected dataFilesBulkService = inject(DataFilesBulkService);

  private searchTerm = '';

  private readonly colDownload: BdDataColumn<LogDataPath> = {
    id: 'download',
    name: 'Downl.',
    data: (r) => `Download ${r.children.length ? 'Folder' : 'File'}`,
    action: (r) => this.doDownload(r),
    icon: () => 'cloud_download',
    width: '50px',
  };

  private defaultColumns: BdDataColumn<LogDataPath>[] = [
    colAvatar,
    colName,
    colItems,
    colModTime,
    colSize,
    this.colDownload,
  ];
  private searchColumns: BdDataColumn<LogDataPath>[] = [colAvatar, colPath, colItems, colModTime, colSize];

  protected columns = this.defaultColumns;

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<LogDataPath[]>(null);
  protected noactive$ = new BehaviorSubject<boolean>(true);

  protected nodes$ = new BehaviorSubject<LogDataPath[]>(null);
  protected tabIndex: number = -1;
  protected selectedPath: LogDataPath;
  protected crumbs: CrumbInfo[] = [];

  protected instance: InstanceDto;
  private subscription: Subscription;

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
      this.lds.directories$.subscribe((dd) => {
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
        if (!nodes?.length || !route?.params || !route?.params['path']) {
          this.selectedPath = null;
          this.records$.next(null);
          return;
        }
        const path = decodeDataFilePath(route.params['path']);
        const node = nodes.find((node) => node.minion === path.minion);
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
    this.searchTerm = value ? value : '';
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
    this.lds.load();
  }

  protected onClick(row: LogDataPath) {
    if (!row.entry) {
      this.selectPath(row);
    } else {
      this.router.navigate([
        '',
        {
          outlets: {
            panel: ['panels', 'instances', 'log-files', row.directory.minion, row.entry.path, 'view'],
          },
        },
      ]);
    }
  }

  protected selectPath(path: LogDataPath) {
    this.router.navigate(['..', encodeDataFilePath(path)], { relativeTo: this.activatedRoute });
  }

  protected onTabChange(e: MatTabChangeEvent) {
    if (this.tabIndex !== e.index) {
      this.selectPath(this.nodes$.value[e.index]);
    }
  }

  private doDownload(r: LogDataPath) {
    if (r.children.length) {
      const dataFiles = toFileList(r);
      this.dataFilesBulkService.downloadDataFiles(dataFiles);
    } else {
      this.instances.download(r.directory, r.entry);
    }
  }
}
