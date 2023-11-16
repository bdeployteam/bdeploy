import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
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
import {
  constructDataFilePaths,
  decodeDataFilePath,
  encodeDataFilePath,
  findDataFilePath,
  toFileList,
} from 'src/app/modules/panels/instances/utils/data-file-utils';
import { ServersService } from '../../../servers/services/servers.service';
import { DataFilesBulkService } from '../../services/data-files-bulk.service';
import { DataFilePath, DataFilesService, FileListEntry } from '../../services/data-files.service';
import { InstancesService } from '../../services/instances.service';

const colPath: BdDataColumn<DataFilePath> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const colSize: BdDataColumn<DataFilePath> = {
  id: 'size',
  name: 'Size',
  data: (r) => (r.entry ? r.entry.size : undefined),
  width: '100px',
  showWhen: '(min-width: 700px)',
  component: BdDataSizeCellComponent,
};

const colModTime: BdDataColumn<DataFilePath> = {
  id: 'lastMod',
  name: 'Last Modification',
  data: (r) => (r.entry ? r.entry.lastModified : undefined),
  width: '155px',
  showWhen: '(min-width: 800px)',
  component: BdDataDateCellComponent,
};

const colAvatar: BdDataColumn<DataFilePath> = {
  id: 'avatar',
  name: '',
  data: (r) => (!r.entry ? 'folder' : 'insert_drive_file'),
  width: '75px',
  component: BdDataIconCellComponent,
};

@Component({
  selector: 'app-data-files',
  templateUrl: './data-files.component.html',
})
export class DataFilesComponent implements OnInit, OnDestroy {
  private instances = inject(InstancesService);
  private df = inject(DataFilesService);
  private areas = inject(NavAreasService);
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  protected cfg = inject(ConfigService);
  protected servers = inject(ServersService);
  protected authService = inject(AuthenticationService);
  protected dataFilesBulkService = inject(DataFilesBulkService);

  private readonly colDownload: BdDataColumn<DataFilePath> = {
    id: 'download',
    name: 'Downl.',
    data: (r) => `Download ${r.children.length ? 'Folder' : 'File'}`,
    action: (r) => this.doDownload(r),
    icon: () => 'cloud_download',
    width: '50px',
  };

  private readonly colDelete: BdDataColumn<DataFilePath> = {
    id: 'delete',
    name: 'Delete',
    data: () => 'Delete File',
    action: (r) => this.doDelete(r),
    icon: () => 'delete',
    width: '50px',
    actionDisabled: () => !this.authService.isCurrentScopeWrite(),
  };
  protected columns: BdDataColumn<DataFilePath>[] = [
    colAvatar,
    colPath,
    colModTime,
    colSize,
    this.colDownload,
    this.colDelete,
  ];

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<DataFilePath[]>(null);
  protected noactive$ = new BehaviorSubject<boolean>(true);

  protected nodes$ = new BehaviorSubject<DataFilePath[]>(null);
  protected tabIndex: number = -1;
  protected selectedPath: DataFilePath;
  protected crumbs: CrumbInfo[] = [];

  protected instance: InstanceDto;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

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
      this.df.directories$.subscribe((dd) => {
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
        this.records$.next(this.selectedPath?.children);
        // if path encoded node is not found, select first node (which should be master)
        if (!node) {
          this.selectPath(nodes[0]);
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected load(inst: InstanceDto) {
    this.instance = inst;
    this.noactive$.next(!inst?.activeVersion?.tag);

    if (this.noactive$.value || !this.servers.isSynchronized(inst?.managedServer)) {
      this.loading$.next(false);
      return;
    }

    this.loading$.next(true);
    this.df.load();
  }

  protected onClick(row: DataFilePath) {
    if (!row.entry) {
      this.selectPath(row);
    } else {
      this.router.navigate([
        '',
        {
          outlets: {
            panel: ['panels', 'instances', 'data-files', row.directory.minion, row.entry.path, 'view'],
          },
        },
      ]);
    }
  }

  protected selectPath(path: DataFilePath) {
    this.router.navigate(['..', encodeDataFilePath(path)], { relativeTo: this.activatedRoute });
  }

  protected onTabChange(e: MatTabChangeEvent) {
    if (this.tabIndex !== e.index) {
      this.selectPath(this.nodes$.value[e.index]);
    }
  }

  private doDelete(r: DataFilePath) {
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
          this.dataFilesBulkService
            .deleteFiles(dataFiles)
            .pipe(finalize(() => this.load(this.instance)))
            .subscribe();
        }
      });
  }

  private doDownload(r: DataFilePath) {
    if (r.children.length) {
      const dataFiles = toFileList(r);
      this.dataFilesBulkService.downloadDataFiles(dataFiles);
    } else {
      this.instances.download(r.directory, r.entry);
    }
  }
}
