import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceDto, MinionMode, ObjectChangeType } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { NavAreasService } from '../../../core/services/nav-areas.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';

@Injectable({
  providedIn: 'root',
})
export class InstancesService {
  loading$ = new BehaviorSubject<boolean>(true);
  instances$ = new BehaviorSubject<InstanceDto[]>([]);

  private group: string;
  private subscription: Subscription;

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    areas: NavAreasService
  ) {
    areas.groupContext$.subscribe((group) => this.reload(group));
  }

  public synchronize(instance: string) {
    if (this.cfg.config.mode === MinionMode.CENTRAL) {
      // TODO: do something
    }
  }

  public isSynchronized(instance: string): boolean {
    if (this.cfg.config.mode === MinionMode.CENTRAL) {
      // TODO: determine
    }
    return true;
  }

  private reload(group: string) {
    if (!group) {
      return;
    }

    if (this.group !== group) {
      this.updateChangeSubscription(group);
    }

    this.group = group;
    this.loading$.next(true);
    this.http
      .get<InstanceDto[]>(`${this.apiPath(group)}`)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((instances) => {
        this.instances$.next(instances);
      });
  }

  private updateChangeSubscription(group: string) {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }

    this.subscription = this.changes.subscribe(ObjectChangeType.INSTANCE, { scope: [group] }, () => {
      this.reload(group);
    });
  }
}
