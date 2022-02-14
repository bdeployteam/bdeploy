import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';

export interface BdSearchable {
  bdOnSearch(value: string): void;
}

@Injectable({
  providedIn: 'root',
})
export class SearchService {
  private currentSearch = '';
  private registrations: BdSearchable[] = [];
  public enableSearch$ = new BehaviorSubject<boolean>(false);

  set search(value: string) {
    this.registrations.forEach((r) => {
      // try to update. in complex naviation cases, this may fail as widgets are in the process of being destroyed.
      try {
        r.bdOnSearch(value);
      } catch (e) {
        console.warn('Cannot update searchable', e);
      }
    });
    this.currentSearch = value;
  }

  get search() {
    return this.currentSearch;
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
    this.search = '';

    setTimeout(() => {
      this.enableSearch$.next(this.registrations.length > 0);
    });
  }
}
