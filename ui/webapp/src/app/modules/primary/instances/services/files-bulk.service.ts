import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { toFileList } from 'src/app/modules/panels/instances/utils/data-file-utils';
import { GroupsService } from '../../groups/services/groups.service';
import { FileListEntry, FilePath } from './files.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class FilesBulkService {
  private readonly areas = inject(NavAreasService);
  private readonly cfg = inject(ConfigService);
  private readonly groups = inject(GroupsService);
  private readonly instances = inject(InstancesService);
  private readonly downloads = inject(DownloadService);
  private readonly http = inject(HttpClient);
  private readonly apiPath = (g: string, i: string) => `${this.cfg.config.api}/group/${g}/instance/${i}`;

  public selection: FilePath[] = [];

  /** Clears the selection when the primary route changes. */
  constructor() {
    this.areas.primaryRoute$.subscribe(() => (this.selection = []));
  }

  get selectedFiles(): FileListEntry[] {
    return this.selection.flatMap((dfp) => toFileList(dfp));
  }

  public deleteFiles(minion: string, files: FileListEntry[]): Observable<unknown> {
    return this.http
      .post(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instances.current$.value.instanceConfiguration.id,
        )}/delete/${minion}`,
        files.map((file) => file.entry),
      )
      .pipe(measure('Delete Instance Data Files'));
  }

  public downloadFiles(files: FileListEntry[]) {
    const path = this.apiPath(this.groups.current$.value.name, this.instances.current$.value.instanceConfiguration.id);
    const minion = files[0]?.directory?.minion;
    const entries: RemoteDirectoryEntry[] = [];

    for (const sel of files) {
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
