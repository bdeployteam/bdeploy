import { Component, OnInit } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { LogColumnsService } from 'src/app/modules/primary/admin/services/log-columns.service';
import { HiveLoggingService } from '../../../services/hive-logging.service';

@Component({
  selector: 'app-bhive-log-browser',
  templateUrl: './bhive-log-browser.component.html',
  styleUrls: ['./bhive-log-browser.component.css'],
})
export class BhiveLogBrowserComponent implements OnInit {
  private readonly colDownload: BdDataColumn<RemoteDirectoryEntry> = {
    id: 'download',
    name: 'D/L',
    data: (r) => `Download ${r.path}`,
    width: '40px',
    action: (r) => this.download(this.directory$.value, r),
    icon: () => 'cloud_download',
  };

  /* template */ columns: BdDataColumn<RemoteDirectoryEntry>[] = [
    ...this.cols.defaultColumns,
    this.colDownload,
  ];
  /* template */ records$ = new BehaviorSubject<RemoteDirectoryEntry[]>([]);
  /* template */ sort: Sort = { active: 'modified', direction: 'desc' };
  /* template */ directory$ = new BehaviorSubject<RemoteDirectory>(null);

  private _index = 0;
  /* template */ set selectedIndex(index: number) {
    this._index = index;
    if (this.hiveLogging.directories$.value?.length) {
      this.directory$.next(this.hiveLogging.directories$.value[index]);
    } else {
      this.directory$.next(null);
    }
  }

  /* template */ get selectedIndex(): number {
    return this._index;
  }

  /* template */ getRecordRoute = (row: RemoteDirectoryEntry) => {
    return [
      '',
      {
        outlets: {
          panel: [
            'panels',
            'admin',
            'bhive',
            this.hiveLogging.bhive$.value,
            'logs',
            this.directory$.value.minion,
            row.path,
          ],
        },
      },
    ];
  };

  public activeRemoteDirectory: RemoteDirectory = null;
  public activeRemoteDirectoryEntry: RemoteDirectoryEntry = null;

  constructor(
    public authService: AuthenticationService,
    public hiveLogging: HiveLoggingService,
    private cols: LogColumnsService
  ) {}

  public ngOnInit(): void {
    this.hiveLogging.directories$.subscribe((dirs) => {
      if (!dirs) {
        this.directory$.next(null);
      } else {
        this.directory$.next(dirs[0]);
      }
    });
    this.hiveLogging.reload();
  }

  private download(
    remoteDirectory: RemoteDirectory,
    remoteDirectoryEntry: RemoteDirectoryEntry
  ) {
    this.hiveLogging.downloadLogFileContent(
      remoteDirectory,
      remoteDirectoryEntry
    );
  }
}
