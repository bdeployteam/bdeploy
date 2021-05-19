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
  private registrations: BdSearchable[] = [];
  private enableSearch$ = new BehaviorSubject<boolean>(false);
  private log = this.logging.getLogger('SearchService');

  constructor(private logging: LoggingService) {}

  set search(value: string) {
    this.registrations.forEach((r) => r.bdOnSearch(value));
    this.currentSearch = value;
  }

  get search() {
    return this.currentSearch;
  }

  enabled() {
    return this.enableSearch$;
  }

  register(searchable: BdSearchable): Subscription {
    this.registrations.push(searchable);

    // enable async to avoid errors while components are still being created.
    setTimeout(() => this.enableSearch$.next(true));

    return new Subscription(() => {
      this.deregister(searchable);
    });
  }

  private deregister(searchable: BdSearchable) {
    this.registrations = this.registrations.filter((x) => x !== searchable);

    // WHENEVER some searchable deregisters, clear the search since that must be some kind of navigation.
    this.currentSearch = '';

    setTimeout(() => {
      this.enableSearch$.next(this.registrations.length > 0);
    });
  }
}
