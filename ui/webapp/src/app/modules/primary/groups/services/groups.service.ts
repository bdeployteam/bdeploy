import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, forkJoin, Observable } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import {
  CustomAttributeDescriptor,
  CustomAttributesRecord,
  InstanceGroupConfiguration,
  ObjectChangeDetails,
  ObjectChangeHint,
  ObjectChangeType,
  ObjectId,
} from 'src/app/models/gen.dtos';
import { LoggingService } from 'src/app/modules/core/services/logging.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from '../../../core/services/object-changes.service';
import { SettingsService } from '../../../core/services/settings.service';

@Injectable({
  providedIn: 'root',
})
export class GroupsService {
  private log = this.logging.getLogger('GroupsService');

  private apiPath = `${this.cfg.config.api}/group`;
  private update$ = new BehaviorSubject<any>(null);

  loading$ = new BehaviorSubject<boolean>(true);

  /** All instance groups */
  groups$ = new BehaviorSubject<InstanceGroupConfiguration[]>([]);

  /** The "current" group based on the current route context */
  current$ = new BehaviorSubject<InstanceGroupConfiguration>(null);

  /** The "current" group's attribute values */
  currentAttributeValues$ = new BehaviorSubject<CustomAttributesRecord>(null);

  /** All *global* attribute definitions */
  attributeDefinitions$ = new BehaviorSubject<CustomAttributeDescriptor[]>([]);

  /** All attribute values for all groups */
  attributeValues$ = new BehaviorSubject<{ [index: string]: CustomAttributesRecord }>({});

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private areas: NavAreasService,
    private settings: SettingsService,
    private logging: LoggingService
  ) {
    this.areas.groupContext$.subscribe((r) => this.setCurrent(r));
    this.update$.pipe(debounceTime(100)).subscribe((_) => this.reload());
    this.changes.subscribe(ObjectChangeType.INSTANCE_GROUP, EMPTY_SCOPE, (change) => {
      if (change.details[ObjectChangeDetails.CHANGE_HINT] === ObjectChangeHint.SERVERS) {
        // ignore changes in managed servers, those as handled in ServersService.
        return;
      }
      this.update$.next(change);
    });
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
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Group Load')
      )
      .subscribe((result) => {
        this.groups$.next(result.groups);

        // set the attribute values before the definitions - consumers will react when definitions change and try to extract values from records
        this.attributeValues$.next(result.attributes);

        const attrDefs = this.settings.getSettings().instanceGroup?.attributes;
        this.attributeDefinitions$.next(!!attrDefs ? attrDefs : []);

        // last update the current$ subject to inform about changes
        if (!!this.areas.groupContext$.value) {
          this.setCurrent(this.areas.groupContext$.value);
        }
      });
  }

  private setCurrent(group: string) {
    this.currentAttributeValues$.next(this.attributeValues$.value[group]);
    this.current$.next(this.groups$.value.find((g) => g.name === group));
  }
}
