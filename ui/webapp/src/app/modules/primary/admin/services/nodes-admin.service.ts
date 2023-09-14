import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, debounceTime, finalize, Observable } from 'rxjs';
import {
  ManifestKey,
  MinionStatusDto,
  NodeAttachDto,
  ObjectChangeType,
  RemoteService,
  RepairAndPruneResultDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';

export interface MinionRecord {
  name: string;
  status: MinionStatusDto;
}

@Injectable({
  providedIn: 'root',
})
export class NodesAdminService {
  private changes = inject(ObjectChangesService);
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);

  public loading$ = new BehaviorSubject<boolean>(true);
  public nodes$ = new BehaviorSubject<MinionRecord[]>(null);

  private update$ = new BehaviorSubject<void>(null);
  private apiPath = () => `${this.cfg.config.api}/node-admin`;

  constructor() {
    this.changes.subscribe(ObjectChangeType.NODES, EMPTY_SCOPE, () => {
      this.update$.next(null);
    });

    this.update$.pipe(debounceTime(100)).subscribe(() => this.reload());
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

  public addNode(dto: NodeAttachDto): Observable<any> {
    return this.http.put(`${this.apiPath()}/nodes`, dto).pipe(measure(`Add node ${dto.name}`));
  }

  public editNode(nodeName: string, remote: RemoteService): Observable<any> {
    return this.http.post(`${this.apiPath()}/nodes/${nodeName}`, remote).pipe(measure(`Edit node ${nodeName}`));
  }

  public replaceNode(nodeName: string, remote: RemoteService): Observable<any> {
    return this.http
      .post(`${this.apiPath()}/nodes/${nodeName}/replace`, remote)
      .pipe(measure(`Replace node ${nodeName}`));
  }

  public removeNode(nodeName: string): Observable<any> {
    return this.http.delete(`${this.apiPath()}/nodes/${nodeName}`).pipe(measure(`Remove node ${nodeName}`));
  }

  public repairAndPruneNode(nodeName: string): Observable<RepairAndPruneResultDto> {
    return this.http
      .post<RepairAndPruneResultDto>(`${this.apiPath()}/nodes/${nodeName}/repair-and-prune`, null)
      .pipe(measure(`Repair and Prune node ${nodeName}`));
  }
}
