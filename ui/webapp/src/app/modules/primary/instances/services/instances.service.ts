import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import { InstanceConfiguration, InstanceDto, ObjectChangeType } from 'src/app/models/gen.dtos';
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

  private group: string;
  private subscription: Subscription;
  private update$ = new BehaviorSubject<string>(null);

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(private cfg: ConfigService, private http: HttpClient, private changes: ObjectChangesService, groups: GroupsService) {
    groups.current$.subscribe((group) => this.update$.next(group?.name));
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
      .subscribe((instances) => this.instances$.next(instances));
  }

  private updateChangeSubscription(group: string) {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }

    this.subscription = this.changes.subscribe(ObjectChangeType.INSTANCE, { scope: [group] }, () => {
      this.update$.next(group);
    });
  }
}
