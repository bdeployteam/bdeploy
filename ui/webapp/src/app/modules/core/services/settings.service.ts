import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { finalize, first, skipWhile, switchMap, tap } from 'rxjs/operators';
import { MailReceiverSettingsDto, MailSenderSettingsDto, SettingsConfiguration } from 'src/app/models/gen.dtos';
import { measure } from '../utils/performance.utils';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class SettingsService {
  private readonly config = inject(ConfigService);
  private readonly http = inject(HttpClient);

  public loading$ = new BehaviorSubject<boolean>(true);
  public settings$ = new BehaviorSubject<SettingsConfiguration>(null);
  public settingsUpdated$ = new BehaviorSubject<boolean>(false);

  private origSettings: SettingsConfiguration;

  constructor() {
    this.load();
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
      switchMap(() => this.settings$),
      first()
    );
  }

  public discard() {
    this.settings$.next(cloneDeep(this.origSettings));
    this.settingsUpdated$.next(false);
  }

  public save() {
    if (!this.isDirty()) {
      return of(null);
    }
    this.loading$.next(true);
    return this.http
      .post<SettingsConfiguration>(this.config.config.api + '/master/settings', this.settings$.value)
      .pipe(
        tap(() => {
          this.load();
        })
      );
  }

  public addLdapServer(server): Observable<boolean> {
    this.settings$.value.auth.ldapSettings.push(server);
    this.settingsUpdated$.next(true);
    return of(true);
  }

  public editLdapServer(server, initialServer) {
    this.settings$.value.auth.ldapSettings.splice(
      this.settings$.value.auth.ldapSettings.indexOf(initialServer),
      1,
      server
    );
    this.settingsUpdated$.next(true);
  }

  public removeLdapServer(server) {
    this.settings$.value.auth.ldapSettings.splice(this.settings$.value.auth.ldapSettings.indexOf(server), 1);
    this.settingsUpdated$.next(true);
  }

  public addGlobalAttribute(attribute): Observable<boolean> {
    this.settings$.value.instanceGroup.attributes.push(attribute);
    this.settingsUpdated$.next(true);
    return of(true);
  }

  public editGlobalAttribute(attribute, initialAttribute) {
    this.settings$.value.instanceGroup.attributes.splice(
      this.settings$.value.instanceGroup.attributes.indexOf(initialAttribute),
      1,
      attribute
    );
    this.settingsUpdated$.next(true);
  }

  public removeAttribute(attribute) {
    this.settings$.value.instanceGroup.attributes.splice(
      this.settings$.value.instanceGroup.attributes.indexOf(attribute),
      1
    );
    this.settingsUpdated$.next(true);
  }

  public serversReordered() {
    this.settingsUpdated$.next(true);
  }

  /**
   * Prompts the backend to attempt to send a mail with the parameters defined within the given mail sender settings.
   *
   * @param dto The MailSenderSettingsDto which contains the configuration that will be used to send the test mail
   */
  public sendTestMail(dto: MailSenderSettingsDto): Observable<boolean> {
    return this.http.post<boolean>(this.config.config.api + '/master/settings/mail/sending/sendTestMail', dto);
  }

  /**
   * Prompts the backend to attempt to connect to the server with the parameters defined within the given mail sender settings.
   *
   * @param dto The MailSenderSettingsDto which contains the configuration that will be used to test the connection
   */
  public testSenderConnection(dto: MailSenderSettingsDto): Observable<boolean> {
    return this.http.post<boolean>(this.config.config.api + '/master/settings/mail/sending/connectionTest', dto);
  }

  /**
   * Prompts the backend to attempt to connect to the server with the parameters defined within the given mail receiver settings.
   *
   * @param dto The MailReceiverSettingsDto which contains the configuration that will be used to test the connection
   */
  public testReceiverConnection(dto: MailReceiverSettingsDto): Observable<boolean> {
    return this.http.post<boolean>(this.config.config.api + '/master/settings/mail/receiving/connectionTest', dto);
  }
}
