import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  CustomAttributeDescriptor,
  CustomAttributesRecord,
  InstanceGroupConfiguration,
  ObjectChangeDto,
  ObjectChangeType,
  ObjectId,
} from 'src/app/models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { EMPTY_SCOPE, ObjectChangesService } from '../../core/services/object-changes.service';
import { SettingsService } from '../../core/services/settings.service';

@Injectable({
  providedIn: 'root',
})
export class GroupsService {
  private apiPath = this.cfg.config.api + '/group';

  loading$ = new BehaviorSubject<boolean>(true);
  groups$ = new BehaviorSubject<InstanceGroupConfiguration[]>([]);
  attributeDefinitions$ = new BehaviorSubject<CustomAttributeDescriptor[]>([]);
  attributeValues$ = new BehaviorSubject<{ [index: string]: CustomAttributesRecord }>({});

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    public settings: SettingsService
  ) {
    this.reload();
    this.changes.subscribe(ObjectChangeType.INSTANCE_GROUP, EMPTY_SCOPE, (change) => this.onChange(change));
  }

  public getLogoUrlOrDefault(group: string, id: ObjectId, def: string) {
    if (!id) {
      return def;
    }

    return `${this.apiPath}/${group}/image?logo=${id.id}`;
  }

  private reload() {
    this.loading$.next(true);
    forkJoin({
      groups: this.http.get<InstanceGroupConfiguration[]>(this.apiPath),
      attributes: this.http.get<{ [index: string]: CustomAttributesRecord }>(this.apiPath + '/list-attributes'),
      settings: this.settings.waitUntilLoaded(),
    })
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((result) => {
        this.groups$.next(result.groups);

        // set the attribute values before the definitions - consumers will react when definitions change and try to extract values from records
        this.attributeValues$.next(result.attributes);

        const attrDefs = this.settings.getSettings().instanceGroup?.attributes;
        this.attributeDefinitions$.next(!!attrDefs ? attrDefs : []);
      });
  }

  private onChange(change: ObjectChangeDto) {
    // TODO: better single record updating...?
    this.reload();
  }
}
