import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import { ObjectChangeDetails, ObjectChangeHint, ObjectChangeType, SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { LoggingService } from 'src/app/modules/core/services/logging.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { EMPTY_SCOPE, ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';

@Injectable({
  providedIn: 'root',
})
export class RepositoriesService {
  private log = this.logging.getLogger('RepositoriesService');

  private apiPath = `${this.cfg.config.api}/softwarerepository`;
  private update$ = new BehaviorSubject<any>(null);

  loading$ = new BehaviorSubject<boolean>(true);

  /** All software repositories */
  repositories$ = new BehaviorSubject<SoftwareRepositoryConfiguration[]>([]);

  /** The *current* repository based on the current route context */
  current$ = new BehaviorSubject<SoftwareRepositoryConfiguration>(null);

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private areas: NavAreasService,
    private logging: LoggingService
  ) {
    this.areas.repositoryContext$.subscribe((r) => this.setCurrent(r));
    this.update$.pipe(debounceTime(100)).subscribe((_) => this.reload());
    this.changes.subscribe(ObjectChangeType.SOFTWARE_REPO, EMPTY_SCOPE, (change) => {
      if (change.details[ObjectChangeDetails.CHANGE_HINT] === ObjectChangeHint.SERVERS) {
        // ignore changes in managed servers, those as handled in ServersService.
        return;
      }
      this.update$.next(change);
    });
  }

  public create(repository: Partial<SoftwareRepositoryConfiguration>): Observable<any> {
    return this.http.put(this.apiPath, repository);
  }

  private reload() {
    this.loading$.next(true);
    this.http
      .get<SoftwareRepositoryConfiguration[]>(this.apiPath)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Software Repositories Load')
      )
      .subscribe((result) => {
        this.repositories$.next(result);

        if (!!this.areas.repositoryContext$.value) {
          this.setCurrent(this.areas.repositoryContext$.value);
        }
      });
  }

  private setCurrent(repository: string) {
    this.current$.next(this.repositories$.value.find((r) => r.name === repository));
  }
}
