import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import { InstanceConfiguration, InstanceDto, ObjectChangeType } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';
import { GroupsService } from '../../groups/services/groups.service';

@Injectable({
  providedIn: 'root',
})
export class InstancesService {
  loading$ = new BehaviorSubject<boolean>(true);
  instances$ = new BehaviorSubject<InstanceDto[]>([]);

  current$ = new BehaviorSubject<InstanceDto>(null);

  private group: string;
  private subscription: Subscription;
  private update$ = new BehaviorSubject<string>(null);

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private areas: NavAreasService,
    groups: GroupsService
  ) {
    groups.current$.subscribe((group) => this.update$.next(group?.name));
    areas.instanceContext$.subscribe((i) => this.setCurrent(i));
    this.update$.pipe(debounceTime(100)).subscribe((g) => this.reload(g));
  }

  public create(instance: Partial<InstanceConfiguration>, managedServer: string) {
    return this.http.put(`${this.apiPath(this.group)}`, instance, { params: { managedServer } });
  }

  private reload(group: string) {
    if (!group) {
      this.instances$.next([]);
      return;
    }

    if (this.group !== group) {
      this.updateChangeSubscription(group);
    }

    this.group = group;
    this.loading$.next(true);
    this.http
      .get<InstanceDto[]>(`${this.apiPath(group)}`)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Instance Load')
      )
      .subscribe((instances) => {
        this.instances$.next(instances);

        // last update the current$ subject to inform about changes
        if (!!this.areas.instanceContext$.value) {
          this.setCurrent(this.areas.instanceContext$.value);
        }
      });
  }

  private updateChangeSubscription(group: string) {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }

    this.subscription = this.changes.subscribe(ObjectChangeType.INSTANCE, { scope: [group] }, () => {
      this.update$.next(group);
    });
  }

  private setCurrent(i: string) {
    this.current$.next(this.instances$.value?.find((x) => x.instanceConfiguration.uuid === i));
  }
}
