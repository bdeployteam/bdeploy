import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { first, mergeMap, skipWhile } from 'rxjs/operators';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { CrumbInfo } from 'src/app/modules/core/components/bd-breadcrumbs/bd-breadcrumbs.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

export interface FileListEntry {
  directory: RemoteDirectory;
  entry: RemoteDirectoryEntry;
}

export interface LogDataPath {
  crumbs: CrumbInfo[];
  minion: string;
  name: string;
  path: string;
  children: LogDataPath[];
  directory: RemoteDirectory;
  entry: RemoteDirectoryEntry;
  lastModified: number;
  size: number;
}

@Injectable({
  providedIn: 'root',
})
export class LogDataService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private groups = inject(GroupsService);
  private instances = inject(InstancesService);

  public directories$ = new BehaviorSubject<RemoteDirectory[]>(null);

  private apiPath = (g, i) => `${this.cfg.config.api}/group/${g}/instance/${i}`;

  public load() {
    this.instances.current$
      .pipe(
        skipWhile((i) => !i),
        first(),
        mergeMap((i) =>
          this.http
            .get<
              RemoteDirectory[]
            >(`${this.apiPath(this.groups.current$.value.name, i.instanceConfiguration.id)}/processes/logDataDirSnapshot`)
            .pipe(measure('Load Instance Log Data Files.')),
        ),
      )
      .subscribe((d) => this.directories$.next(d));
  }
}
