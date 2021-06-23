import { Component, OnInit, ViewChild } from '@angular/core';
import { format } from 'date-fns';
import { BehaviorSubject } from 'rxjs';
import { first, skipWhile } from 'rxjs/operators';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { formatSize } from 'src/app/modules/core/utils/object.utils';
import { ServersService } from '../../../servers/services/servers.service';
import { DataFilesService } from '../../services/data-files.service';
import { InstancesService } from '../../services/instances.service';

interface FileListEntry {
  directory: RemoteDirectory;
  entry: RemoteDirectoryEntry;
}

const colPath: BdDataColumn<FileListEntry> = {
  id: 'path',
  name: 'File Path',
  data: (r) => r.entry.path,
};

const colSize: BdDataColumn<FileListEntry> = {
  id: 'size',
  name: 'File Size',
  data: (r) => formatSize(r.entry.size),
  width: '100px',
  showWhen: '(min-width: 700px)',
};

const colModTime: BdDataColumn<FileListEntry> = {
  id: 'lastMod',
  name: 'Last Modification',
  data: (r) => format(r.entry.lastModified, 'dd.MM.yyyy HH:mm'),
  width: '150px',
  showWhen: '(min-width: 800px)',
};

@Component({
  selector: 'app-data-files',
  templateUrl: './data-files.component.html',
  styleUrls: ['./data-files.component.css'],
})
export class DataFilesComponent implements OnInit {
  private readonly colDelete: BdDataColumn<FileListEntry> = {
    id: 'delete',
    name: 'Delete',
    data: (r) => 'Delete File',
    action: (r) => this.doDelete(r),
    icon: (r) => 'delete',
    width: '40px',
  };

  private readonly colDownload: BdDataColumn<FileListEntry> = {
    id: 'download',
    name: 'Downl.',
    data: (r) => r,
    action: (r) => this.doDownload(r),
    icon: (r) => 'cloud_download',
    width: '40px',
  };

  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ records$ = new BehaviorSubject<FileListEntry[]>(null);
  /* template */ noactive$ = new BehaviorSubject<boolean>(false);
  /* template */ columns: BdDataColumn<FileListEntry>[] = [colPath, colModTime, colSize, this.colDelete, this.colDownload];
  /* template */ grouping: BdDataGrouping<FileListEntry>[] = [{ definition: { group: (r) => r.directory.minion, name: 'Node Name' }, selected: [] }];
  /* template */ getRecordRoute = (row: FileListEntry) => {
    return ['', { outlets: { panel: ['panels', 'instances', 'data-files', row.directory.minion, row.entry.path] } }];
  };

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(public cfg: ConfigService, public instances: InstancesService, public servers: ServersService, private df: DataFilesService) {}

  ngOnInit(): void {
    this.load();

    this.servers.servers$.subscribe((_) => {
      this.load();
    });

    this.df.directories$.subscribe((dd) => {
      if (!dd) {
        this.records$.next(null);
        return;
      }

      const entries: FileListEntry[] = [];
      for (const dir of dd) {
        if (!!dir.problem) {
          console.warn(`Problem reading files from ${dir.minion}: ${dir.problem}`);
          continue;
        }
        for (const entry of dir.entries) {
          entries.push({ directory: dir, entry: entry });
        }
      }
      this.records$.next(entries);
      this.loading$.next(false);
    });
  }

  /* template */ load() {
    this.instances.current$
      .pipe(
        skipWhile((i) => !i),
        first()
      )
      .subscribe((i) => {
        this.noactive$.next(!i?.activeVersion?.tag);

        if (this.noactive$.value || !this.servers.isSynchronized(i.managedServer)) {
          this.loading$.next(false);
          return;
        }

        this.loading$.next(true);
        this.df.load();
      });
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
          this.df.deleteFile(r.directory, r.entry).subscribe((_) => {
            this.load();
          });
        }
      });
  }

  private doDownload(r: FileListEntry) {
    this.instances.download(r.directory, r.entry);
  }
}
