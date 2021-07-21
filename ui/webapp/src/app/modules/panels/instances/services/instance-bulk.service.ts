import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, concat, Observable, of } from 'rxjs';
import { concatAll, filter, map, mergeMap } from 'rxjs/operators';
import { ApplicationValidationDto, InstanceDto, InstanceUpdateDto, ProductDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Injectable({
  providedIn: 'root',
})
export class InstanceBulkService {
  public selection$ = new BehaviorSubject<InstanceDto[]>([]);
  public frozen$ = new BehaviorSubject<boolean>(false);

  private apiPath = (group, instance) => `${this.cfg.config.api}/group/${group}/instance/${instance}`;

  constructor(private cfg: ConfigService, private http: HttpClient, private groups: GroupsService, private instance: InstancesService, areas: NavAreasService) {
    // clear selection when the primary route changes
    areas.primaryRoute$.subscribe((r) => this.selection$.next([]));

    // find matching selected instances if possible once instances change.
    instance.instances$.subscribe((i) => {
      const newSelection: InstanceDto[] = [];
      this.selection$.value.forEach((inst) => {
        const found = i.find((i) => i.instanceConfiguration.uuid === inst.instanceConfiguration.uuid);
        if (found) {
          newSelection.push(found);
        }
      });
      this.selection$.next(newSelection);
    });
  }

  public start() {
    return concat(
      this.selection$.value.map((inst) => this.http.get(`${this.apiPath(this.groups.current$.value.name, inst.instanceConfiguration.uuid)}/processes/start`))
    ).pipe(concatAll());
  }

  public stop() {
    return concat(
      this.selection$.value.map((inst) => this.http.get(`${this.apiPath(this.groups.current$.value.name, inst.instanceConfiguration.uuid)}/processes/stop`))
    ).pipe(concatAll());
  }

  public prepareUpdate(target: ProductDto): Observable<InstanceUpdateDto> {
    return concat(this.selection$.value).pipe(
      filter((i) => i.instanceConfiguration.product.tag !== target.key.tag),
      mergeMap((i) =>
        this.instance.loadNodes(i.instanceConfiguration.uuid, i.instance.tag).pipe(
          map((nodes) => {
            const upd: InstanceUpdateDto = { config: { config: i.instanceConfiguration, nodeDtos: nodes.nodeConfigDtos }, files: [], validation: [] };
            return upd;
          })
        )
      ),
      mergeMap((u) =>
        this.http.post<InstanceUpdateDto>(`${this.apiPath(this.groups.current$.value.name, u.config.config.uuid)}/updateProductVersion/${target.key.tag}`, u)
      ),
      mergeMap((u) =>
        this.http.post<ApplicationValidationDto[]>(`${this.apiPath(this.groups.current$.value.name, u.config.config.uuid)}/validate`, u).pipe(
          map((v) => {
            return { config: u.config, files: [], validation: v };
          })
        )
      )
    );
  }

  public saveUpdate(updates: InstanceUpdateDto[]): Observable<any> {
    return of(...updates).pipe(
      mergeMap((u) => {
        const dto = this.selection$.value.find((i) => i.instanceConfiguration.uuid === u.config.config.uuid);
        const managedServer = dto.managedServer?.hostName;
        const expect = dto.instance.tag;
        return this.http.post(`${this.apiPath(this.groups.current$.value.name, u.config.config.uuid)}/update`, u, { params: { managedServer, expect } });
      })
    );
  }

  public delete() {
    return concat(
      this.selection$.value.map((inst) => this.http.delete(`${this.apiPath(this.groups.current$.value.name, inst.instanceConfiguration.uuid)}/delete`))
    ).pipe(concatAll());
  }
}
