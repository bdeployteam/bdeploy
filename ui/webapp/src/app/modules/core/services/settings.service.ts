import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, Observable } from 'rxjs';
import { first, skipWhile } from 'rxjs/operators';
import { SettingsConfiguration } from 'src/app/models/gen.dtos';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root',
})
export class SettingsService {
  private loading$ = new BehaviorSubject<boolean>(true);
  private settings: SettingsConfiguration;
  private origSettings: SettingsConfiguration;

  constructor(private config: ConfigService, private http: HttpClient) {
    // TODO: service is not providedIn root - need reload strategy.
    this.load();
  }

  private load() {
    this.loading$.next(true);
    this.http.get<SettingsConfiguration>(this.config.config.api + '/master/settings').subscribe((r) => {
      this.settings = r;
      this.origSettings = cloneDeep(this.settings);
      this.loading$.next(false);
    });
  }

  public isDirty(): boolean {
    return !isEqual(this.settings, this.origSettings);
  }

  public isLoading(): boolean {
    return this.loading$.value;
  }

  public waitUntilLoaded(): Observable<boolean> {
    return this.loading$.pipe(
      skipWhile((v) => v === true),
      first()
    );
  }

  public getSettings(): SettingsConfiguration {
    return this.settings;
  }

  public revert() {
    this.load();
  }

  public save() {
    this.loading$.next(true);
    this.http.post<SettingsConfiguration>(this.config.config.api + '/master/settings', this.settings).subscribe((_) => {
      this.load();
    });
  }
}
