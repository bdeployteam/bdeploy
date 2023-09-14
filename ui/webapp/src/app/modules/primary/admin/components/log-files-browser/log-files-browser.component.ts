import { Component, OnInit, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { RemoteDirectory, RemoteDirectoryEntry } from '../../../../../models/gen.dtos';
import { LogColumnsService } from '../../services/log-columns.service';
import { LoggingAdminService } from '../../services/logging-admin.service';

@Component({
  selector: 'app-log-files-browser',
  templateUrl: './log-files-browser.component.html',
})
export class LogFilesBrowserComponent implements OnInit {
  private cols = inject(LogColumnsService);
  protected authService = inject(AuthenticationService);
  protected loggingAdmin = inject(LoggingAdminService);

  private readonly colDownload: BdDataColumn<RemoteDirectoryEntry> = {
    id: 'download',
    name: 'D/L',
    data: (r) => `Download ${r.path}`,
    width: '40px',
    action: (r) => this.download(this.directory$.value, r),
    icon: () => 'cloud_download',
  };

  protected columns: BdDataColumn<RemoteDirectoryEntry>[] = [...this.cols.defaultColumns, this.colDownload];
  protected records$ = new BehaviorSubject<RemoteDirectoryEntry[]>([]);
  protected sort: Sort = { active: 'modified', direction: 'desc' };
  protected directory$ = new BehaviorSubject<RemoteDirectory>(null);

  private _index = 0;
  protected set selectedIndex(index: number) {
    this._index = index;
    if (this.loggingAdmin.directories$.value?.length) {
      this.directory$.next(this.loggingAdmin.directories$.value[index]);
    } else {
      this.directory$.next(null);
    }
  }

  protected get selectedIndex(): number {
    return this._index;
  }

  protected getRecordRoute = (row: RemoteDirectoryEntry) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'admin', 'logging', 'view', this.directory$.value.minion, row.path],
        },
      },
    ];
  };

  public activeRemoteDirectory: RemoteDirectory = null;
  public activeRemoteDirectoryEntry: RemoteDirectoryEntry = null;

  public ngOnInit(): void {
    this.loggingAdmin.directories$.subscribe((dirs) => {
      this.directory$.next(dirs[0]);
    });
    this.loggingAdmin.reload();
  }

  private download(remoteDirectory: RemoteDirectory, remoteDirectoryEntry: RemoteDirectoryEntry) {
    this.loggingAdmin.downloadLogFileContent(remoteDirectory, remoteDirectoryEntry);
  }
}
