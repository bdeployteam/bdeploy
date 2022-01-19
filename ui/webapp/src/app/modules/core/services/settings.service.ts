import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize, first, skipWhile, switchMap, tap } from 'rxjs/operators';
import { SettingsConfiguration } from 'src/app/models/gen.dtos';
import { measure } from '../utils/performance.utils';
import { ConfigService } from './config.service';
import { NavAreasService } from './nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class SettingsService {
  public loading$ = new BehaviorSubject<boolean>(true);
  public settings$ = new BehaviorSubject<SettingsConfiguration>(null);
  public settingsUpdated$ = new BehaviorSubject<boolean>(false);

  private origSettings: SettingsConfiguration;

  constructor(private config: ConfigService, private http: HttpClient, private areas: NavAreasService) {
    this.load();

    areas.adminRoute$.subscribe((r) => {
      if (!r) {
        this.discard();
      }
    });
  }

  private load() {
    this.loading$.next(true);
    this.http
      .get<SettingsConfiguration>(this.config.config.api + '/master/settings')
      .pipe(
        measure('Load Settings'),
        finalize(() => this.loading$.next(false))
      )
      .subscribe((r) => {
        this.settings$.next(r);
        this.origSettings = cloneDeep(r);
      });
  }

  public isDirty(): boolean {
    return !isEqual(this.settings$.value, this.origSettings);
  }

  public waitUntilLoaded(): Observable<SettingsConfiguration> {
    return this.loading$.pipe(
      skipWhile((v) => v === true),
      switchMap((_) => this.settings$),
      first()
    );
  }

  public discard() {
    this.settings$.next(cloneDeep(this.origSettings));
    this.settingsUpdated$.next(false);
  }

  public save() {
    if (!this.isDirty()) {
      return;
    }
    this.loading$.next(true);
    return this.http.post<SettingsConfiguration>(this.config.config.api + '/master/settings', this.settings$.value).pipe(
      tap((_) => {
        this.load();
      })
    );
  }

  public addLdapServer(server) {
    this.settings$.value.auth.ldapSettings.push(server);
    this.settingsUpdated$.next(true);
    this.areas.closePanel();
  }

  public editLdapServer(server, initialServer) {
    this.settings$.value.auth.ldapSettings.splice(this.settings$.value.auth.ldapSettings.indexOf(initialServer), 1, server);
    this.settingsUpdated$.next(true);
  }

  public removeLdapServer(server) {
    this.settings$.value.auth.ldapSettings.splice(this.settings$.value.auth.ldapSettings.indexOf(server), 1);
    this.settingsUpdated$.next(true);
  }

  public addGlobalAttribute(attribute) {
    this.settings$.value.instanceGroup.attributes.push(attribute);
    this.settingsUpdated$.next(true);
    this.areas.closePanel();
  }

  public editGlobalAttribute(attribute, initialAttribute) {
    this.settings$.value.instanceGroup.attributes.splice(this.settings$.value.instanceGroup.attributes.indexOf(initialAttribute), 1, attribute);
    this.settingsUpdated$.next(true);
  }

  public removeAttribute(attribute) {
    this.settings$.value.instanceGroup.attributes.splice(this.settings$.value.instanceGroup.attributes.indexOf(attribute), 1);
    this.settingsUpdated$.next(true);
  }

  public serversReordered() {
    this.settingsUpdated$.next(true);
  }
}
