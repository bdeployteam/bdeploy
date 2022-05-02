import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, forkJoin, Observable } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import {
  CustomAttributeDescriptor,
  CustomAttributesRecord,
  CustomDataGrouping,
  InstanceGroupConfiguration,
  InstanceGroupConfigurationDto,
  ObjectChangeDetails,
  ObjectChangeHint,
  ObjectChangeType,
  ObjectId,
} from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import {
  EMPTY_SCOPE,
  ObjectChangesService,
} from '../../../core/services/object-changes.service';
import { SettingsService } from '../../../core/services/settings.service';

@Injectable({
  providedIn: 'root',
})
export class GroupsService {
  private apiPath = `${this.cfg.config.api}/group`;
  private update$ = new BehaviorSubject<any>(null);

  loading$ = new BehaviorSubject<boolean>(true);

  /** All instance groups */
  groups$ = new BehaviorSubject<InstanceGroupConfigurationDto[]>([]);

  /** The "current" group based on the current route context */
  current$ = new BehaviorSubject<InstanceGroupConfiguration>(null);

  /** The "current" group's attribute values */
  currentAttributeValues$ = new BehaviorSubject<CustomAttributesRecord>(null);

  /** All *global* attribute definitions */
  attributeDefinitions$ = new BehaviorSubject<CustomAttributeDescriptor[]>([]);

  /** All attribute values for all groups */
  attributeValues$ = new BehaviorSubject<{
    [index: string]: CustomAttributesRecord;
  }>({});

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private areas: NavAreasService,
    private settings: SettingsService
  ) {
    this.areas.groupContext$.subscribe((r) => this.setCurrent(r));
    this.update$.pipe(debounceTime(100)).subscribe(() => this.reload());
    this.changes.subscribe(
      ObjectChangeType.INSTANCE_GROUP,
      EMPTY_SCOPE,
      (change) => {
        if (
          change.details[ObjectChangeDetails.CHANGE_HINT] ===
          ObjectChangeHint.SERVERS
        ) {
          // ignore changes in managed servers, those as handled in ServersService.
          return;
        }
        this.update$.next(change);
      }
    );
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
        if (r) {
          this.http
            .get(`${this.apiPath}/${r.name}/new-uuid`, { responseType: 'text' })
            .subscribe((uuid) => {
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

  public removeImage(group: string) {
    return this.http.delete<Response>(`${this.apiPath}/${group}/image`);
  }

  public updatePreset(
    group: string,
    preset: CustomDataGrouping[],
    multiple: boolean
  ): Observable<any> {
    return this.http.put<any>(
      `${this.apiPath}/${group}/presets?multiple=${multiple}`,
      preset
    );
  }

  private reload() {
    this.loading$.next(true);
    forkJoin({
      groups: this.http.get<InstanceGroupConfigurationDto[]>(this.apiPath),
      attributes: this.http.get<{ [index: string]: CustomAttributesRecord }>(
        `${this.apiPath}/list-attributes`
      ),
    })
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Group Load')
      )
      .subscribe((result) => {
        this.groups$.next(result.groups);

        // set the attribute values before the definitions - consumers will react when definitions change and try to extract values from records
        this.attributeValues$.next(result.attributes);

        // last update the current$ subject to inform about changes
        if (this.areas.groupContext$.value) {
          this.setCurrent(this.areas.groupContext$.value);
        }
      });

    this.settings.settings$.subscribe((s) => {
      if (s) {
        this.attributeDefinitions$.next(s?.instanceGroup?.attributes);
      }
    });
  }

  private setCurrent(group: string) {
    this.currentAttributeValues$.next(this.attributeValues$.value[group]);

    const current = this.current$.value;
    const updated = this.groups$.value
      .map((dto) => dto.instanceGroupConfiguration)
      .find((g) => g.name === group);

    if (isEqual(current, updated)) {
      return;
    }

    this.current$.next(updated);
  }
}
