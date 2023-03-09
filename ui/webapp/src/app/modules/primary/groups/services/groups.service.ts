import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, forkJoin, Observable } from 'rxjs';
import {
  debounceTime,
  finalize,
  first,
  skipWhile,
  switchMap,
} from 'rxjs/operators';
import {
  CustomAttributeDescriptor,
  CustomAttributesRecord,
  CustomDataGrouping,
  InstanceGroupConfiguration,
  InstanceGroupConfigurationDto,
  ObjectChangeDetails,
  ObjectChangeDto,
  ObjectChangeHint,
  ObjectChangeType,
  ObjectEvent,
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

const INIT_GROUPS = [];

@Injectable({
  providedIn: 'root',
})
export class GroupsService {
  private apiPath = `${this.cfg.config.api}/group`;
  private update$ = new BehaviorSubject<ObjectChangeDto>(null);

  loading$ = new BehaviorSubject<boolean>(true);

  /** All instance groups */
  groups$ = new BehaviorSubject<InstanceGroupConfigurationDto[]>(INIT_GROUPS);

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
    private settings: SettingsService,
    private snackbar: MatSnackBar,
    private router: Router
  ) {
    this.areas.groupContext$.subscribe((r) => this.setCurrent(r));
    this.update$
      .pipe(debounceTime(100))
      .subscribe((change) => this.onGroupsChanged(change));
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

  private onGroupsChanged(change: ObjectChangeDto) {
    const scopeLength = change?.scope?.scope.length;
    if (!!change && scopeLength !== 1) {
      console.warn(
        `Unexpected instance group change scope length: ${scopeLength}. Reloading.`
      );
      this.reload();
      return;
    }

    switch (change?.event) {
      case ObjectEvent.CREATED:
      case ObjectEvent.CHANGED:
        this.onGroupUpdated(change.scope.scope[0]);
        break;
      case ObjectEvent.REMOVED:
        this.onGroupDeleted(change.scope.scope[0]);
        break;
      default:
        this.reload();
    }
  }

  private onGroupUpdated(groupName: string) {
    this.loading$.next(true);
    forkJoin({
      group: this.http.get<InstanceGroupConfigurationDto>(
        `${this.apiPath}/${groupName}`
      ),
      attribute: this.http.get<CustomAttributesRecord>(
        `${this.apiPath}/${groupName}/attributes`
      ),
    })
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe(({ group, attribute }) => {
        const oldGroup = this.groups$.value.find(
          (g) => g.instanceGroupConfiguration.name === groupName
        );
        const groups = this.groups$.value.filter((g) => g !== oldGroup);
        groups.push(group);
        groups.sort((a, b) =>
          a.instanceGroupConfiguration.name.localeCompare(
            b.instanceGroupConfiguration.name
          )
        );
        this.groups$.next(groups);

        const attr = this.attributeValues$.value;
        attr[groupName] = attribute;
        this.attributeValues$.next({ ...attr });

        // last update the current$ subject to inform about changes
        if (this.areas.groupContext$.value) {
          this.setCurrent(this.areas.groupContext$.value);
        }
      });
  }

  private onGroupDeleted(groupName: string) {
    const groups = this.groups$.value.filter(
      (g) => g.instanceGroupConfiguration.name !== groupName
    );
    this.groups$.next(groups);

    const attr = this.attributeValues$.value;
    delete attr[groupName];
    this.attributeValues$.next({ ...attr });
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

  public newId(): Observable<string> {
    return this.current$.pipe(
      skipWhile((g) => !g),
      switchMap((g) =>
        this.http.get(`${this.apiPath}/${g.name}/new-uuid`, {
          responseType: 'text',
        })
      )
    );
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

    this.settings.settings$.pipe(first()).subscribe((s) => {
      if (s) {
        this.attributeDefinitions$.next(s?.instanceGroup?.attributes);
      }
    });
  }

  private setCurrent(group: string) {
    this.currentAttributeValues$.next(this.attributeValues$.value[group]);

    const groups = this.groups$.value;
    const current = this.current$.value;
    const updated = groups
      .map((dto) => dto.instanceGroupConfiguration)
      .find((g) => g.name === group);

    const notFound = !!group && !updated && groups !== INIT_GROUPS;
    if (notFound) {
      this.onNotFound();
      return;
    }

    if (isEqual(current, updated)) {
      return;
    }

    this.current$.next(updated);
  }

  private onNotFound() {
    this.snackbar.open(
      `Unfortunately, ${this.router.url} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`,
      'DISMISS',
      { panelClass: 'error-snackbar' }
    );
    this.areas.forcePanelClose$.next(true);
    this.router.navigate(['/groups/browser'], {
      state: { ignoreDirtyGuard: true },
    });
  }
}
