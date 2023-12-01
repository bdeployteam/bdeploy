import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { mergeMap } from 'rxjs/operators';
import { RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { toFileList } from 'src/app/modules/panels/instances/utils/data-file-utils';
import { GroupsService } from '../../groups/services/groups.service';
import { DataFilePath, DataFilesService, FileListEntry } from './data-files.service';
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
  private dataFilesService = inject(DataFilesService);

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

  public deleteFiles(dataFiles: FileListEntry[]): Observable<any> {
    // FIXME: issue only a single call, not multiple!
    return of(...dataFiles).pipe(mergeMap((file) => this.dataFilesService.deleteFile(file.directory, file.entry)));
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
