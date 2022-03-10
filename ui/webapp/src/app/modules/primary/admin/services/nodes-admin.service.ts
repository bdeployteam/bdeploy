import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, finalize, Observable } from 'rxjs';
import {
  ManifestKey,
  MinionStatusDto,
  ObjectChangeType,
  RemoteService,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import {
  EMPTY_SCOPE,
  ObjectChangesService,
} from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';

export interface MinionRecord {
  name: string;
  status: MinionStatusDto;
}

@Injectable({
  providedIn: 'root',
})
export class NodesAdminService {
  public loading$ = new BehaviorSubject<boolean>(true);
  public nodes$ = new BehaviorSubject<MinionRecord[]>(null);

  private apiPath = () => `${this.cfg.config.api}/node-admin`;

  constructor(
    private changes: ObjectChangesService,
    private cfg: ConfigService,
    private http: HttpClient
  ) {
    this.changes.subscribe(ObjectChangeType.NODES, EMPTY_SCOPE, () => {
      this.reload();
    });

    this.reload();
  }

  public reload() {
    this.loading$.next(true);
    this.http
      .get<{ [name: string]: MinionStatusDto }>(`${this.apiPath()}/nodes`)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((nodes) => {
        const minions = Object.keys(nodes).map((name) => ({
          name,
          status: nodes[name],
        }));
        this.nodes$.next(minions);
      });
  }

  public updateNode(nodeName: string, keys: ManifestKey[]): Observable<string> {
    return this.http
      .post<string>(`${this.apiPath()}/nodes/${nodeName}/update`, keys)
      .pipe(measure(`Update node ${nodeName}`));
  }

  public addNode(nodeName: string, remote: RemoteService): Observable<any> {
    return this.http
      .put(`${this.apiPath()}/nodes/${nodeName}`, remote)
      .pipe(measure(`Add node ${nodeName}`));
  }

  public editNode(nodeName: string, remote: RemoteService): Observable<any> {
    return this.http
      .post(`${this.apiPath()}/nodes/${nodeName}`, remote)
      .pipe(measure(`Edit node ${nodeName}`));
  }

  public removeNode(nodeName: string): Observable<any> {
    return this.http
      .delete(`${this.apiPath()}/nodes/${nodeName}`)
      .pipe(measure(`Remove node ${nodeName}`));
  }

  public fsckNode(nodeName: string): Observable<{ [key: string]: string }> {
    return this.http
      .post<{ [key: string]: string }>(
        `${this.apiPath()}/nodes/${nodeName}/fsck`,
        null
      )
      .pipe(measure(`Fsck node ${nodeName}`));
  }

  public pruneNode(nodeName: string): Observable<number> {
    return this.http
      .post<number>(`${this.apiPath()}/nodes/${nodeName}/prune`, null)
      .pipe(measure(`Prune node ${nodeName}`));
  }
}
