import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from '../../groups/services/groups.service';
import { DataFilesService } from './data-files.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class DataFilesBulkService {
  public selection: {
    directory: RemoteDirectory;
    entry: RemoteDirectoryEntry;
  }[] = [];
  public frozen$ = new BehaviorSubject<boolean>(false);

  private apiPath = (g, i) => `${this.cfg.config.api}/group/${g}/instance/${i}`;

  constructor(
    areas: NavAreasService,
    private cfg: ConfigService,
    private groups: GroupsService,
    private instances: InstancesService,
    private downloads: DownloadService,
    private http: HttpClient,
    private dataFilesService: DataFilesService
  ) {
    // clear selection when the primary route changes
    areas.primaryRoute$.subscribe(() => (this.selection = []));
  }

  async deleteFiles(): Promise<any> {
    return Promise.all(
      this.selection.map(
        (file) =>
          new Promise((resolve) =>
            this.dataFilesService
              .deleteFile(file.directory, file.entry)
              .subscribe((data) => resolve(data))
          )
      )
    ).then((data: any) => data);
  }

  public downloadDataFile() {
    const path = this.apiPath(
      this.groups.current$.value.name,
      this.instances.current$.value.instanceConfiguration.id
    );

    const minion = this.selection[0]?.directory?.minion;
    const entries: RemoteDirectoryEntry[] = [];
    for (const sel of this.selection) {
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
