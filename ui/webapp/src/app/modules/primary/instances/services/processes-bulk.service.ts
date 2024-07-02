import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessesBulkService {
  private readonly areas = inject(NavAreasService);

  public selection$ = new BehaviorSubject<{
    [key: string]: ApplicationConfiguration[];
  }>({});

  constructor() {
    // clear selection when the primary route changes
    this.areas.primaryRoute$.subscribe(() => this.selection$.next({}));
  }

  public update(node: string, selection: ApplicationConfiguration[]) {
    this.selection$.next({ ...this.selection$.value, [node]: selection });
  }
}
