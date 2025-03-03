import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, finalize, forkJoin, map, Observable } from 'rxjs';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import {
  RepositoryService, SwPkgType,
  SwRepositoryEntry
} from 'src/app/modules/primary/repositories/services/repository.service';

@Injectable({
  providedIn: 'root',
})
export class SoftwareDetailsBulkService {
  private readonly areas = inject(NavAreasService);
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly repositories = inject(RepositoriesService);
  private readonly repository = inject(RepositoryService);

  private readonly productApiPath = (r: string) => `${this.cfg.config.api}/softwarerepository/${r}/product/bulk`;
  private readonly softwareApiPath = (r: string) => `${this.cfg.config.api}/softwarerepository/${r}/content/bulk`;

  public selection$ = new BehaviorSubject<SwRepositoryEntry[]>([]);
  public frozen$ = new BehaviorSubject<boolean>(false);

  constructor() {
    // clear selection when the primary route changes
    this.areas.primaryRoute$.subscribe(() => this.selection$.next([]));

    // find matching selected items if possible once repository data changes.
    this.repository.data$.subscribe((pkgs) => {
      const newSelection: SwRepositoryEntry[] = [];
      this.selection$.value.forEach((s) => {
        const found = pkgs.find(
          (pkg) => pkg.key.name === s.key.name && pkg.key.tag === s.key.tag && pkg.type === s.type,
        );
        if (found) {
          newSelection.push(found);
        }
      });
      this.selection$.next(newSelection);
    });
  }

  public delete(): Observable<BulkOperationResultDto> {
    const selected = this.selection$.value;
    const repo = this.repositories.current$.value.name;
    const deleteProducts$ = this.http.post<BulkOperationResultDto>(
      `${this.productApiPath(repo)}/delete`,
      selected.filter((i) => i.type === SwPkgType.PRODUCT).map((i) => i.key),
    );
    const deleteSoftwarePackages$ = this.http.post<BulkOperationResultDto>(
      `${this.softwareApiPath(repo)}/delete`,
      selected.filter((i) => i.type === SwPkgType.EXTERNAL_SOFTWARE).map((i) => i.key),
    );
    this.frozen$.next(true);
    return forkJoin([deleteProducts$, deleteSoftwarePackages$]).pipe(
      map(([p, s]) => ({ results: [...p.results, ...s.results] })),
      finalize(() => this.frozen$.next(false)),
    );
  }
}
