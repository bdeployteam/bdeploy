import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { first, mergeMap, skipWhile } from 'rxjs/operators';
import { RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class DataFilesService {
  public directories$ = new BehaviorSubject<RemoteDirectory[]>(null);

  private apiPath = (g, i) => `${this.cfg.config.api}/group/${g}/instance/${i}`;

  constructor(private cfg: ConfigService, private http: HttpClient, private groups: GroupsService, private instances: InstancesService) {}

  public load() {
    this.instances.current$
      .pipe(
        skipWhile((i) => !i),
        first(),
        mergeMap((i) =>
          this.http
            .get<RemoteDirectory[]>(`${this.apiPath(this.groups.current$.value.name, i.instanceConfiguration.uuid)}/processes/dataDirSnapshot`)
            .pipe(measure('Load Instance Data Files.'))
        )
      )
      .subscribe((d) => this.directories$.next(d));
  }

  public deleteFile(rd: RemoteDirectory, rde: RemoteDirectoryEntry): Observable<any> {
    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name, this.instances.current$.value.instanceConfiguration.uuid)}/delete/${rd.minion}`, rde)
      .pipe(measure('Delete Instance Data File'));
  }
}
