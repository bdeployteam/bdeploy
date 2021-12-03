import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { HistoryEntryDto, HistoryFilterDto } from 'src/app/models/gen.dtos';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class HistoryService {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ history$ = new BehaviorSubject<HistoryEntryDto[]>(null);
  /* template */ filter$ = new BehaviorSubject<Partial<HistoryFilterDto>>({
    maxResults: 100,
    showCreateEvents: true,
    showDeploymentEvents: true,
    showRuntimeEvents: false,
  });

  private subscription: Subscription;

  constructor(private instances: InstancesService) {}

  /** Begins publishing history on the history$ subject */
  public begin() {
    this.subscription = this.filter$.subscribe((filter) => {
      this.history$.next(null);

      if (!this.instances.current$.value) {
        return;
      }

      this.update(filter);
    });

    this.subscription.add(this.instances.current$.subscribe(() => this.reset()));
  }

  /** Stops reading and publishing history and resets all internal state */
  public stop() {
    this.subscription.unsubscribe();
    this.reset();
  }

  /** Continues loading history, picking up from the last known point */
  public more() {
    if (!!this.filter$.value.startTag) {
      this.update(this.filter$.value);
    }
  }

  private reset() {
    this.history$.next(null);
    this.filter$.next({ ...this.filter$.value, startTag: null }); // reset, start over
  }

  private update(filter: Partial<HistoryFilterDto>) {
    this.loading$.next(true);
    this.instances
      .loadHistory(filter)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((result) => {
        const oldStart = filter.startTag;

        // will continue loading next time.
        filter.startTag = result.next;

        if (!!oldStart) {
          const arr = !!this.history$.value ? [...this.history$.value] : [];
          arr.push(...result.events);
          this.history$.next(arr);
        } else {
          this.history$.next(result.events);
        }
      });
  }
}
