import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { LoggingService } from './logging.service';

export interface BdSearchable {
  bdOnSearch(value: string): void;
}

@Injectable({
  providedIn: 'root',
})
export class SearchService {
  private currentSearch = '';
  private registration$ = new BehaviorSubject<BdSearchable>(null);
  private log = this.logging.getLogger('SearchService');

  constructor(private logging: LoggingService) {}

  set search(value: string) {
    if (this.registration$.value) {
      this.registration$.value.bdOnSearch(value);
    }
    this.currentSearch = value;
  }

  get search() {
    return this.currentSearch;
  }

  registration(): BehaviorSubject<BdSearchable> {
    return this.registration$;
  }

  register(searchable: BdSearchable): Subscription {
    if (this.registration$.value) {
      this.log.warn(`Search consumer already registered: ${JSON.stringify(this.registration$.value)}`);
    }
    this.registration$.next(searchable);
    return new Subscription(() => {
      this.deregister(searchable);
    });
  }

  private deregister(searchable: BdSearchable) {
    if (this.registration$.value !== searchable) {
      this.log.warn(
        `Registered search consumer different from given: ${JSON.stringify(
          this.registration$.value
        )} != ${JSON.stringify(searchable)}`
      );
    }
    this.registration$.next(null);
    this.currentSearch = '';
  }
}
