import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { formatSize } from 'src/app/modules/core/utils/object.utils';
import { RemoteDirectory, RemoteDirectoryEntry } from '../../../../../models/gen.dtos';
import { LoggingAdminService } from '../../services/logging-admin.service';

const COL_AVATAR: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'avatar',
  name: '',
  data: (r) => 'subject',
  width: '40px',
  component: BdDataIconCellComponent,
};

const COL_PATH: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'path',
  name: 'Path',
  data: (r) => r.path,
};

const COL_SIZE: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'size',
  name: 'Size',
  data: (r) => formatSize(r.size),
  width: '80px',
};

const COL_MODIFIED: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'modified',
  name: 'Last Modified',
  data: (r) => r.lastModified,
  width: '130px',
  component: BdDataDateCellComponent,
};

@Component({
  selector: 'app-log-files-browser',
  templateUrl: './log-files-browser.component.html',
  styleUrls: ['./log-files-browser.component.css'],
})
export class LogFilesBrowserComponent implements OnInit {
  private readonly colDownload: BdDataColumn<RemoteDirectoryEntry> = {
    id: 'download',
    name: 'D/L',
    data: (r) => `Download ${r.path}`,
    width: '40px',
    action: (r) => this.download(this.directory$.value, r),
    icon: (r) => 'cloud_download',
  };

  /* template */ columns: BdDataColumn<RemoteDirectoryEntry>[] = [COL_AVATAR, COL_PATH, COL_SIZE, COL_MODIFIED, this.colDownload];
  /* template */ records$ = new BehaviorSubject<RemoteDirectoryEntry[]>([]);
  /* template */ sort: Sort = { active: 'modified', direction: 'desc' };
  /* template */ directory$ = new BehaviorSubject<RemoteDirectory>(null);

  private _index = 0;
  /* template */ set selectedIndex(index: number) {
    this._index = index;
    if (!!this.loggingAdmin.directories$.value?.length) {
      this.directory$.next(this.loggingAdmin.directories$.value[index]);
    } else {
      this.directory$.next(null);
    }
  }

  /* template */ get selectedIndex(): number {
    return this._index;
  }

  /* template */ getRecordRoute = (row: RemoteDirectoryEntry) => {
    return ['', { outlets: { panel: ['panels', 'admin', 'logging', 'view', this.directory$.value.minion, row.path] } }];
  };

  public activeRemoteDirectory: RemoteDirectory = null;
  public activeRemoteDirectoryEntry: RemoteDirectoryEntry = null;

  constructor(public location: Location, public authService: AuthenticationService, public loggingAdmin: LoggingAdminService) {}

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
