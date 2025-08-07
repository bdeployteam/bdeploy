import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { first, mergeMap, skipWhile } from 'rxjs/operators';
import { FileStatusDto, RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { CrumbInfo } from 'src/app/modules/core/components/bd-breadcrumbs/bd-breadcrumbs.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

export interface FileListEntry {
  directory: RemoteDirectory;
  entry: RemoteDirectoryEntry;
}

export interface FilePath {
  crumbs: CrumbInfo[];
  minion: string;
  name: string;
  path: string;
  children: FilePath[];
  directory: RemoteDirectory;
  entry: RemoteDirectoryEntry;
  lastModified: number;
  size: number;
}

@Injectable({
  providedIn: 'root',
})
export class FilesService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly groups = inject(GroupsService);
  private readonly instances = inject(InstancesService);
  private readonly apiPath = (g: string, i: string) => `${this.cfg.config.api}/group/${g}/instance/${i}`;

  public directories$ = new BehaviorSubject<RemoteDirectory[]>(null);

  public updateFile(rd: RemoteDirectory, file: FileStatusDto): Observable<unknown> {
    return this.http
      .post(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instances.current$.value.instanceConfiguration.id,
        )}/data/update/${rd.minion}`,
        [file],
      )
      .pipe(measure('Update Instance File'));
  }

  public loadDataFiles() {
    this.loadFiles('dataDirSnapshot', 'Load Instance Data Files.');
  }

  public loadLogFiles() {
    this.loadFiles('logDataDirSnapshot', 'Load Instance Log Files.');
  }

  private loadFiles(api: string, txt: string) {
    this.directories$.next([]);
    this.instances.current$
      .pipe(
        skipWhile((i) => !i),
        first(),
        mergeMap((i) =>
          this.http
            .get<
              RemoteDirectory[]
            >(`${this.apiPath(this.groups.current$.value.name, i.instanceConfiguration.id)}/processes/${api}`)
            .pipe(measure(txt)),
        ),
      )
      .subscribe((d) => this.directories$.next(d));
  }
}
