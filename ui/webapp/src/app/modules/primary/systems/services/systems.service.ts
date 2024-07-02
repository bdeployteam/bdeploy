import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, finalize } from 'rxjs';
import {
  ObjectChangeType,
  SystemConfigurationDto,
  SystemTemplateRequestDto,
  SystemTemplateResultDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';

@Injectable({
  providedIn: 'root',
})
export class SystemsService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly changes = inject(ObjectChangesService);
  private readonly groups = inject(GroupsService);

  public systems$ = new BehaviorSubject<SystemConfigurationDto[]>([]);
  public loading$ = new BehaviorSubject<boolean>(true);

  private readonly apiPath = (g) => `${this.cfg.config.api}/group/${g}/system`;
  private subscription: Subscription;

  constructor() {
    this.groups.current$.subscribe((g) => {
      this.updateChangeSubscription(g?.name);

      if (!g) {
        this.systems$.next([]);
        return;
      }
      this.load(g.name).subscribe((systems) => {
        this.systems$.next(systems);
      });
    });
  }

  public create(system: SystemConfigurationDto): Observable<any> {
    return this.http
      .post(`${this.apiPath(this.groups.current$.value?.name)}`, system)
      .pipe(measure(`Create system ${system.config.name}`));
  }

  public delete(id: string): Observable<unknown> {
    return this.http
      .delete(`${this.apiPath(this.groups.current$.value?.name)}/${id}`)
      .pipe(measure(`Delete system ${id}`));
  }

  public getTemplateUploadUrl(): string {
    return this.apiPath(this.groups.current$.value?.name);
  }

  public apply(request: SystemTemplateRequestDto): Observable<SystemTemplateResultDto> {
    return this.http.post<SystemTemplateResultDto>(`${this.apiPath(this.groups.current$.value?.name)}/apply`, request);
  }

  private load(group: string): Observable<SystemConfigurationDto[]> {
    this.loading$.next(true);
    return this.http.get<SystemConfigurationDto[]>(`${this.apiPath(group)}`).pipe(
      finalize(() => this.loading$.next(false)),
      measure(`Systems of ${group}`),
    );
  }

  private updateChangeSubscription(group: string) {
    this.subscription?.unsubscribe();
    this.subscription = null;

    if (group) {
      this.subscription = this.changes.subscribe(ObjectChangeType.SYSTEM, { scope: [group] }, () => {
        this.load(group).subscribe((systems) => {
          this.systems$.next(systems);
        });
      });
    }
  }
}
