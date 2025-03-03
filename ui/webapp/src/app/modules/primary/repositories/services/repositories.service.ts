import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, ReplaySubject } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import {
  ObjectChangeDetails,
  ObjectChangeHint,
  ObjectChangeType,
  SoftwareRepositoryConfiguration,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { EMPTY_SCOPE, ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';

const INIT_REPOSITORIES: SoftwareRepositoryConfiguration[] = [];

@Injectable({
  providedIn: 'root',
})
export class RepositoriesService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly changes = inject(ObjectChangesService);
  private readonly areas = inject(NavAreasService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly router = inject(Router);

  private readonly apiPath = `${this.cfg.config.api}/softwarerepository`;
  private readonly update$ = new BehaviorSubject<unknown>(null);

  public loading$ = new BehaviorSubject<boolean>(true);

  /** All software repositories */
  public repositories$ = new ReplaySubject<SoftwareRepositoryConfiguration[]>(1);
  public currentRepositores: SoftwareRepositoryConfiguration[] = INIT_REPOSITORIES;

  /** The *current* repository based on the current route context */
  public current$ = new BehaviorSubject<SoftwareRepositoryConfiguration>(null);

  constructor() {
    this.repositories$.subscribe((repos) => (this.currentRepositores = repos));
    this.areas.repositoryContext$.subscribe((r) => this.setCurrent(r));
    this.update$.pipe(debounceTime(100)).subscribe(() => this.reload());
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
        measure('Software Repositories Load'),
      )
      .subscribe((result) => {
        this.repositories$.next(result);

        if (this.areas.repositoryContext$.value) {
          this.setCurrent(this.areas.repositoryContext$.value);
        }
      });
  }

  private setCurrent(repository: string) {
    const repositories = this.currentRepositores;

    const currentRepository = repositories.find((r) => r.name === repository);

    const notFound = !!repository && !currentRepository && repositories !== INIT_REPOSITORIES;
    if (notFound) {
      this.onNotFound();
      return;
    }

    this.current$.next(currentRepository);
  }

  private onNotFound() {
    this.snackbar.open(
      `Unfortunately, ${this.router.url} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`,
      'DISMISS',
      { panelClass: 'error-snackbar' },
    );
    this.areas.forcePanelClose$.next(true);
    this.router.navigate(['repositories', 'browser'], {
      state: { ignoreDirtyGuard: true },
    });
  }
}
