import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { InstanceDto, RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataSizeCellComponent } from 'src/app/modules/core/components/bd-data-size-cell/bd-data-size-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ServersService } from '../../../servers/services/servers.service';
import { DataFilesBulkService } from '../../services/data-files-bulk.service';
import { DataFilesService } from '../../services/data-files.service';
import { InstancesService } from '../../services/instances.service';

export interface FileListEntry {
  directory: RemoteDirectory;
  entry: RemoteDirectoryEntry;
}

const colPath: BdDataColumn<FileListEntry> = {
  id: 'path',
  name: 'Name',
  data: (r) => r.entry.path,
  isId: true,
};

const colSize: BdDataColumn<FileListEntry> = {
  id: 'size',
  name: 'Size',
  data: (r) => r.entry.size,
  width: '100px',
  showWhen: '(min-width: 700px)',
  component: BdDataSizeCellComponent,
};

const colModTime: BdDataColumn<FileListEntry> = {
  id: 'lastMod',
  name: 'Last Modification',
  data: (r) => r.entry.lastModified,
  width: '155px',
  showWhen: '(min-width: 800px)',
  component: BdDataDateCellComponent,
};

@Component({
  selector: 'app-data-files',
  templateUrl: './data-files.component.html',
})
export class DataFilesComponent implements OnInit, OnDestroy {
  private instances = inject(InstancesService);
  private df = inject(DataFilesService);
  protected cfg = inject(ConfigService);
  protected servers = inject(ServersService);
  protected authService = inject(AuthenticationService);
  protected dataFilesBulkService = inject(DataFilesBulkService);

  private readonly colDownload: BdDataColumn<FileListEntry> = {
    id: 'download',
    name: 'Downl.',
    data: () => 'Download File',
    action: (r) => this.doDownload(r),
    icon: () => 'cloud_download',
    width: '50px',
  };

  private readonly colDelete: BdDataColumn<FileListEntry> = {
    id: 'delete',
    name: 'Delete',
    data: () => 'Delete File',
    action: (r) => this.doDelete(r),
    icon: () => 'delete',
    width: '50px',
    actionDisabled: () => !this.authService.isCurrentScopeWrite(),
  };
  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<FileListEntry[]>(null);
  protected noactive$ = new BehaviorSubject<boolean>(true);
  protected columns: BdDataColumn<FileListEntry>[] = [colPath, colModTime, colSize, this.colDownload, this.colDelete];
  protected grouping: BdDataGrouping<FileListEntry>[] = [
    {
      definition: { group: (r) => r.directory.minion, name: 'Node Name' },
      selected: [],
    },
  ];
  protected getRecordRoute = (row: FileListEntry) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'instances', 'data-files', row.directory.minion, row.entry.path, 'view'],
        },
      },
    ];
  };

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
      }
    );

    this.subscription.add(
      this.df.directories$.subscribe((dd) => {
        if (!dd) {
          this.records$.next(null);
          return;
        }

        const entries: FileListEntry[] = [];
        for (const dir of dd) {
          if (dir.problem) {
            console.warn(`Problem reading files from ${dir.minion}: ${dir.problem}`);
            continue;
          }
          for (const entry of dir.entries) {
            entries.push({ directory: dir, entry: entry });
          }
        }
        this.records$.next(entries);
        this.loading$.next(false);
      })
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

  private doDelete(r: FileListEntry) {
    this.dialog
      .confirm(
        `Delete ${r.entry.path}?`,
        `The file <strong>${r.entry.path}</strong> on node <strong>${r.directory.minion}</strong> will be deleted permanently.`,
        'delete'
      )
      .subscribe((confirm) => {
        if (confirm) {
          this.df.deleteFile(r.directory, r.entry).subscribe(() => {
            this.load(this.instance);
          });
        }
      });
  }

  private doDownload(r: FileListEntry) {
    this.instances.download(r.directory, r.entry);
  }
}
