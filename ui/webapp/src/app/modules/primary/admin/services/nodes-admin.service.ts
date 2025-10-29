import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, debounceTime, finalize, Observable } from 'rxjs';
import {
  CreateMultiNodeDto,
  ManifestKey,
  MinionNodeType,
  MinionStatusDto,
  NodeAttachDto,
  NodeListDto,
  ObjectChangeType,
  OperatingSystem,
  RemoteService,
  RepairAndPruneResultDto
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { DownloadService } from '../../../core/services/download.service';

export interface MinionRecord {
  name: string;
  status: MinionStatusDto;
  parentMultiNode: string;
}

@Injectable({
  providedIn: 'root'
})
export class NodesAdminService {
  private readonly changes = inject(ObjectChangesService);
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly downloads = inject(DownloadService);

  public loading$ = new BehaviorSubject<boolean>(true);
  public nodes$ = new BehaviorSubject<MinionRecord[]>(null);

  private readonly update$ = new BehaviorSubject<void>(null);
  private readonly apiPath = () => `${this.cfg.config.api}/node-admin`;

  constructor() {
    this.changes.subscribe(ObjectChangeType.NODES, EMPTY_SCOPE, () => {
      this.update$.next(null);
    });

    this.update$.pipe(debounceTime(100)).subscribe(() => this.reload());
  }

  public getOperatingSystemOptions() {
    return [
      OperatingSystem.LINUX,
      OperatingSystem.LINUX_AARCH64,
      OperatingSystem.WINDOWS
    ];
  }

  public reload() {
    this.loading$.next(true);
    this.http
      .get<NodeListDto>(`${this.apiPath()}/node-list`)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((nodes) => {
        const minions = Object.keys(nodes.nodes).map((name) => ({
          name,
          status: nodes.nodes[name],
          parentMultiNode: nodes.nodes[name].config.minionNodeType == MinionNodeType.MULTI_RUNTIME ? this.findParentFor(name, nodes) : null
        }));
        this.nodes$.next(minions);
      });
  }

  private findParentFor(name: string, node: NodeListDto): string {
    for (const key of Object.keys(node.multiNodeToRuntimeNodes)) {
      if (node.multiNodeToRuntimeNodes[key].includes(name)) {
        return key;
      }
    }
    return null;
  }

  public updateNode(nodeName: string, keys: ManifestKey[]): Observable<string> {
    return this.http
      .post<string>(`${this.apiPath()}/nodes/${nodeName}/update`, keys)
      .pipe(measure(`Update node ${nodeName}`));
  }

  public addNode(dto: NodeAttachDto): Observable<unknown> {
    return this.http.put(`${this.apiPath()}/nodes`, dto).pipe(measure(`Add node ${dto.name}`));
  }

  public editNode(nodeName: string, remote: RemoteService): Observable<unknown> {
    return this.http.post(`${this.apiPath()}/nodes/${nodeName}`, remote).pipe(measure(`Edit node ${nodeName}`));
  }

  public replaceNode(nodeName: string, remote: RemoteService): Observable<unknown> {
    return this.http
      .post(`${this.apiPath()}/nodes/${nodeName}/replace`, remote)
      .pipe(measure(`Replace node ${nodeName}`));
  }

  public removeNode(nodeName: string): Observable<unknown> {
    return this.http.delete(`${this.apiPath()}/nodes/${nodeName}`).pipe(measure(`Remove node ${nodeName}`));
  }

  public repairAndPruneNode(nodeName: string): Observable<RepairAndPruneResultDto> {
    return this.http
      .post<RepairAndPruneResultDto>(`${this.apiPath()}/nodes/${nodeName}/repair-and-prune`, null)
      .pipe(measure(`Repair and Prune node ${nodeName}`));
  }

  public restartNode(nodeName: string): Observable<unknown> {
    return this.http
      .post<unknown>(`${this.apiPath()}/nodes/${nodeName}/restart`, null)
      .pipe(measure(`Restart node ${nodeName}`));
  }

  public shutdownNode(nodeName: string): Observable<unknown> {
    return this.http
      .post<unknown>(`${this.apiPath()}/nodes/${nodeName}/shutdown`, null)
      .pipe(measure(`Shutdown node ${nodeName}`));
  }

  public addMultiNode(dto: CreateMultiNodeDto): Observable<unknown> {
    return this.http.put(`${this.apiPath()}/multi-nodes`, dto).pipe(measure(`Add multi-node ${dto.name}`));
  }

  public downloadMultiNodeMasterFile(nodeName: string): Observable<unknown> {
    return new Observable((s) => {
      this.http.get(`${this.apiPath()}/multi-nodes/${nodeName}/masterFile`)
        .subscribe({
          next: (data) => {
            this.downloads.downloadJson(nodeName + '-master-file.json', data);
            s.next(null);
            s.complete();
          },
          error: (err) => {
            s.error(err);
            s.complete();
          }
        });
    });
  }


  public static nodeTypeColumnSort(a: MinionNodeType, b: MinionNodeType) {
    return NodesAdminService.getNodeTypeWeight(a) - NodesAdminService.getNodeTypeWeight(b);
  }

  public static multiNodeColumnSort(a: string, b: string) {
    return a === 'None' ? -1 : (b === 'None' ? 1 : a.localeCompare(b));
  }

  private static getNodeTypeWeight(nodeType: MinionNodeType): number {
    switch (nodeType) {
      case MinionNodeType.SERVER:
        return 1;
      case MinionNodeType.MULTI:
        return 2;
      case MinionNodeType.MULTI_RUNTIME:
      default:
        return 3;
    }
  }

}
