import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, forkJoin, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  CustomAttributeDescriptor,
  CustomAttributesRecord,
  InstanceGroupConfiguration,
  ObjectChangeDto,
  ObjectChangeType,
  ObjectId,
} from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ConfigService } from '../../../core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from '../../../core/services/object-changes.service';
import { SettingsService } from '../../../core/services/settings.service';

@Injectable({
  providedIn: 'root',
})
export class GroupsService {
  private apiPath = `${this.cfg.config.api}/group`;

  loading$ = new BehaviorSubject<boolean>(true);
  groups$ = new BehaviorSubject<InstanceGroupConfiguration[]>([]);
  current$ = new BehaviorSubject<InstanceGroupConfiguration>(null);
  attributeDefinitions$ = new BehaviorSubject<CustomAttributeDescriptor[]>([]);
  attributeValues$ = new BehaviorSubject<{ [index: string]: CustomAttributesRecord }>({});

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private areas: NavAreasService,
    public settings: SettingsService
  ) {
    this.reload();
    this.changes.subscribe(ObjectChangeType.INSTANCE_GROUP, EMPTY_SCOPE, (change) => this.onChange(change));
    this.areas.groupContext$.subscribe((r) => this.current$.next(this.groups$.value?.find((g) => g.name === r)));
  }

  public getLogoUrlOrDefault(group: string, id: ObjectId, def: string) {
    if (!id) {
      return def;
    }

    return `${this.apiPath}/${group}/image?logo=${id.id}`;
  }

  public create(group: Partial<InstanceGroupConfiguration>): Observable<any> {
    return this.http.put(this.apiPath, group);
  }

  public newUuid(): Observable<string> {
    return new Observable<string>((s) => {
      const sub = this.current$.subscribe((r) => {
        if (!!r) {
          this.http.get(`${this.apiPath}/${r.name}/new-uuid`, { responseType: 'text' }).subscribe((uuid) => {
            s.next(uuid);
            s.complete();
            sub.unsubscribe();
          });
        }
      });
    });
  }

  public updateImage(group: string, file: File) {
    const formData = new FormData();
    formData.append('image', file, file.name);
    return this.http.post<Response>(`${this.apiPath}/${group}/image`, formData);
  }

  private reload() {
    this.loading$.next(true);
    forkJoin({
      groups: this.http.get<InstanceGroupConfiguration[]>(this.apiPath),
      attributes: this.http.get<{ [index: string]: CustomAttributesRecord }>(`${this.apiPath}/list-attributes`),
      settings: this.settings.waitUntilLoaded(),
    })
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((result) => {
        this.groups$.next(result.groups);

        // set the attribute values before the definitions - consumers will react when definitions change and try to extract values from records
        this.attributeValues$.next(result.attributes);

        const attrDefs = this.settings.getSettings().instanceGroup?.attributes;
        this.attributeDefinitions$.next(!!attrDefs ? attrDefs : []);

        // last update the current$ subject to inform about changes
        if (!!this.areas.groupContext$.value) {
          this.current$.next(this.groups$.value.find((g) => g.name === this.areas.groupContext$.value));
        }
      });
  }

  private onChange(change: ObjectChangeDto) {
    // TODO: better single record updating...?
    if (!this.loading$.value) {
      this.reload();
    }
  }
}
