import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  SoftwareUpdateService,
  SoftwareVersion,
} from 'src/app/modules/primary/admin/services/software-update.service';

@Injectable({
  providedIn: 'root',
})
export class SoftwareVersionBulkService {
  public selection$ = new BehaviorSubject<SoftwareVersion[]>([]);
  public frozen$ = new BehaviorSubject<boolean>(false);

  constructor(areas: NavAreasService, private software: SoftwareUpdateService) {
    // clear selection when the primary route changes
    areas.primaryRoute$.subscribe(() => this.selection$.next([]));
  }

  public delete() {
    const manifestKeys = this.selection$.value.flatMap((softwareVersion) => [
      ...softwareVersion.system,
      ...softwareVersion.launcher,
    ]);
    this.frozen$.next(true);
    return this.software.deleteVersion(manifestKeys).pipe(
      finalize(() => this.frozen$.next(false)),
      tap(() => this.selection$.next([]))
    );
  }
}
