import { Component, OnInit, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { LogColumnsService } from 'src/app/modules/primary/admin/services/log-columns.service';
import { HiveLoggingService } from '../../../services/hive-logging.service';
import { BdDialogComponent } from '../../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatTabGroup, MatTab } from '@angular/material/tabs';
import { BdDataTableComponent } from '../../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-bhive-log-browser',
    templateUrl: './bhive-log-browser.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdDialogContentComponent, MatTabGroup, MatTab, BdDataTableComponent, AsyncPipe]
})
export class BhiveLogBrowserComponent implements OnInit {
  private readonly cols = inject(LogColumnsService);
  protected readonly authService = inject(AuthenticationService);
  protected readonly hiveLogging = inject(HiveLoggingService);

  private readonly colDownload: BdDataColumn<RemoteDirectoryEntry> = {
    id: 'download',
    name: 'D/L',
    data: (r) => `Download ${r.path}`,
    width: '40px',
    action: (r) => this.download(this.directory$.value, r),
    icon: () => 'cloud_download',
  };

  protected readonly columns: BdDataColumn<RemoteDirectoryEntry>[] = [...this.cols.defaultColumns, this.colDownload];
  protected records$ = new BehaviorSubject<RemoteDirectoryEntry[]>([]);
  protected sort: Sort = { active: 'modified', direction: 'desc' };
  protected directory$ = new BehaviorSubject<RemoteDirectory>(null);

  private _index = 0;
  protected set selectedIndex(index: number) {
    this._index = index;
    if (this.hiveLogging.directories$.value?.length) {
      this.directory$.next(this.hiveLogging.directories$.value[index]);
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

  private download(remoteDirectory: RemoteDirectory, remoteDirectoryEntry: RemoteDirectoryEntry) {
    this.hiveLogging.downloadLogFileContent(remoteDirectory, remoteDirectoryEntry);
  }
}
