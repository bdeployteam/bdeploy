import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessesBulkService {
  public selection$ = new BehaviorSubject<ApplicationConfiguration[]>([]);

  constructor(areas: NavAreasService) {
    // clear selection when the primary route changes
    areas.primaryRoute$.subscribe(() => this.selection$.next([]));
  }
}
