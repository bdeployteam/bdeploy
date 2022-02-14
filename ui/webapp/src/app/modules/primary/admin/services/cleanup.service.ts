import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { CleanupGroup } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class CleanupService {
  public loading$ = new BehaviorSubject<boolean>(false);
  public performing$ = new BehaviorSubject<boolean>(false);
  public countdown$ = new BehaviorSubject<number>(-1);
  public cleanup$ = new BehaviorSubject<CleanupGroup[]>(null);

  private apiPath = () => `${this.cfg.config.api}/cleanUi`;
  private cdHandle;

  constructor(private cfg: ConfigService, private http: HttpClient) {}

  public calculateCleanup() {
    clearInterval(this.cdHandle);

    this.loading$.next(true);
    this.http
      .get<CleanupGroup[]>(`${this.apiPath()}`)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Calculate Cleanup')
      )
      .subscribe((groups) => {
        const g = groups.filter((c) => !!c.actions?.length);
        this.cleanup$.next(g?.length ? g : null);
        this.countdown$.next(600);

        this.cdHandle = setInterval(() => {
          const countdown = this.countdown$.value - 1;
          if (countdown > 0) {
            this.countdown$.next(countdown);
          } else {
            clearInterval(this.cdHandle);
            this.countdown$.next(-1);
            this.cleanup$.next(null);
          }
        }, 1000);
      });
  }

  public performCleanup(groups: CleanupGroup[]) {
    clearInterval(this.cdHandle);
    this.countdown$.next(-1);

    this.performing$.next(true);
    return this.http
      .post(`${this.apiPath()}`, groups)
      .pipe(
        finalize(() => {
          this.performing$.next(false);
          this.cleanup$.next(null);
        }),
        measure('Perform Cleanup')
      )
      .subscribe();
  }

  public cancelCleanup() {
    clearInterval(this.cdHandle);
    this.countdown$.next(-1);
    this.cleanup$.next(null);
  }
}
