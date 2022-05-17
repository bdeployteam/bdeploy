import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

interface ReplayCache {
  [someUrl: string]: Observable<any>;
}

@Injectable({
  providedIn: 'root',
})
export class HttpReplayService {
  private cache: ReplayCache = {};

  constructor(private http: HttpClient) {}

  public get<T>(url: string, resetTimeMs = 1000): Observable<T> {
    if (!this.cache[url]) {
      this.cache[url] = this.http.get<T>(url).pipe(shareReplay(1));
      setTimeout(() => delete this.cache[url], resetTimeMs);
    }
    return this.cache[url];
  }
}
