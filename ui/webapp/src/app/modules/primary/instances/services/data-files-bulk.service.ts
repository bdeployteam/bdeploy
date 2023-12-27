import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { toFileList } from 'src/app/modules/panels/instances/utils/data-file-utils';
import { GroupsService } from '../../groups/services/groups.service';
import { DataFilePath, FileListEntry } from './data-files.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class DataFilesBulkService {
  private areas = inject(NavAreasService);
  private cfg = inject(ConfigService);
  private groups = inject(GroupsService);
  private instances = inject(InstancesService);
  private downloads = inject(DownloadService);
  private http = inject(HttpClient);

  public selection: DataFilePath[] = [];

  public frozen$ = new BehaviorSubject<boolean>(false);

  private apiPath = (g, i) => `${this.cfg.config.api}/group/${g}/instance/${i}`;

  constructor() {
    // clear selection when the primary route changes
    this.areas.primaryRoute$.subscribe(() => (this.selection = []));
  }

  get selectedDataFiles(): FileListEntry[] {
    return this.selection.flatMap((dfp) => toFileList(dfp));
  }

  public deleteFiles(minion: string, dataFiles: FileListEntry[]): Observable<any> {
    return this.http
      .post(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instances.current$.value.instanceConfiguration.id,
        )}/delete/${minion}`,
        dataFiles.map((file) => file.entry),
      )
      .pipe(measure('Delete Instance Data Files'));
  }

  public downloadDataFiles(dataFiles: FileListEntry[]) {
    const path = this.apiPath(this.groups.current$.value.name, this.instances.current$.value.instanceConfiguration.id);

    const minion = dataFiles[0]?.directory?.minion;
    const entries: RemoteDirectoryEntry[] = [];
    for (const sel of dataFiles) {
      if (sel.directory.minion !== minion) {
        throw new Error('Cannot download files from multiple minions');
      }
      entries.push(sel.entry);
    }

    this.http
      .post(`${path}/requestMultiZip/${minion}`, entries, {
        responseType: 'text',
      })
      .subscribe((token) => {
        this.downloads.download(`${path}/streamMultiZip/${token}`);
      });
  }
}
