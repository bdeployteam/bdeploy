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
  private registrations: BdSearchable[] = [];
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
    this.registrations.push(searchable);
    this.registration$.next(searchable);
    return new Subscription(() => {
      this.deregister(searchable);
    });
  }

  private deregister(searchable: BdSearchable) {
    const top = this.registrations.pop();
    if (top !== searchable) {
      this.log.warn(`Registered search consumer different from given`);
    }
    if (!this.registrations.length) {
      this.registration$.next(null);
    } else {
      this.registration$.next(this.registrations[this.registrations.length - 1]);
    }
    this.currentSearch = '';
  }
}
