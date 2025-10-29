import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable, Injector, NgZone } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { AuthClientConfig } from '@auth0/auth0-angular';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, combineLatest, firstValueFrom, Observable, of, Subject } from 'rxjs';
import { catchError, distinctUntilChanged, map, retry, switchMap, tap } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { BackendInfoDto, MinionMode, PluginInfoDto, Version, WebAuthSettingsDto } from '../../../models/gen.dtos';
import { ConnectionLostComponent } from '../components/connection-lost/connection-lost.component';
import {
  ConnectionVersionComponent,
  VERSION_DATA,
} from '../components/connection-version/connection-version.component';
import { NO_LOADING_BAR_CONTEXT } from '../utils/loading-bar.util';
import { suppressGlobalErrorHandling, suppressUnauthenticatedDelay } from '../utils/server.utils';
import { ThemeService } from './theme.service';

export interface AppConfig {
  version: Version;
  hostname: string;
  api: string;
  ws: string;
  mode: MinionMode;
}

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private readonly themes = inject(ThemeService); /* dummy: required to bootstrap theming early! */
  private readonly http = inject(HttpClient);
  private readonly overlay = inject(Overlay);
  private readonly auth0 = inject(AuthClientConfig);
  private readonly ngZone = inject(NgZone);
  private readonly icons = inject(MatIconRegistry);
  private readonly sanitizer = inject(DomSanitizer);
  private static readonly defaultBackendSessionId = -1;

  public config: AppConfig;
  public webAuthCfg: WebAuthSettingsDto;
  public initialSession = new Subject<string>();

  public offline$ = new BehaviorSubject<boolean>(false);
  public isCentral$ = new BehaviorSubject<boolean>(false);
  public isManaged$ = new BehaviorSubject<boolean>(false);
  public isStandalone$ = new BehaviorSubject<boolean>(false);
  public isNewGitHubReleaseAvailable$ = new BehaviorSubject<boolean>(false);
  public isUpdateInstallSucceeded$ = new BehaviorSubject<boolean>(false);

  private checkInterval: ReturnType<typeof setInterval>;
  private overlayRef: OverlayRef;
  private versionLock = false;

  private backendTimeOffset = 0;
  private backendOffsetWarning = false;
  private backendSessionId = ConfigService.defaultBackendSessionId;

  // prettier-ignore
  constructor() {
    this.icons.setDefaultFontSetClass('material-symbols-outlined');

    // register all custom icons we want to use with <mat-icon>
    this.icons.addSvgIcon('bdeploy', this.sanitizer.bypassSecurityTrustResourceUrl('assets/logo-single-path-square.svg'));
    this.icons.addSvgIcon('instance-settings', this.sanitizer.bypassSecurityTrustResourceUrl('assets/instance-settings.svg'));
    this.icons.addSvgIcon('start-scheduled', this.sanitizer.bypassSecurityTrustResourceUrl('assets/start_schedule.svg'));
    this.icons.addSvgIcon('stop-scheduled', this.sanitizer.bypassSecurityTrustResourceUrl('assets/stop_schedule.svg'));
    this.icons.addSvgIcon('auth0', this.sanitizer.bypassSecurityTrustResourceUrl('assets/auth0.svg'));
    this.icons.addSvgIcon('okta', this.sanitizer.bypassSecurityTrustResourceUrl('assets/okta.svg'));

    this.icons.addSvgIcon('LINUX', this.sanitizer.bypassSecurityTrustResourceUrl('assets/linux.svg'));
    this.icons.addSvgIcon('LINUX_AARCH64', this.sanitizer.bypassSecurityTrustResourceUrl('assets/linux_aarch64.svg'));
    this.icons.addSvgIcon('AIX', this.sanitizer.bypassSecurityTrustResourceUrl('assets/aix.svg'));
    this.icons.addSvgIcon('UNKNOWN', this.sanitizer.bypassSecurityTrustResourceUrl('assets/indeterminate_question.svg'));
    this.icons.addSvgIcon('WINDOWS', this.sanitizer.bypassSecurityTrustResourceUrl('assets/windows.svg'));
    this.icons.addSvgIcon('WEB', this.sanitizer.bypassSecurityTrustResourceUrl('assets/web.svg'));
    this.icons.addSvgIcon('sort_asc', this.sanitizer.bypassSecurityTrustResourceUrl('assets/sort-asc.svg'));
    this.icons.addSvgIcon('sort_desc', this.sanitizer.bypassSecurityTrustResourceUrl('assets/sort-desc.svg'));

    // check whether the server version changed every minute.
    this.ngZone.runOutsideAngular(() => {
      // *usually* we loose the server connection for a short period when this happens, so the interval is just a fallback.
      this.checkInterval = setInterval(() => this.checkServerVersion(), 60000);
    });

    this.offline$.pipe(distinctUntilChanged()).subscribe((o) => {
      this.ngZone.run(() => {
        if (!o) {
          this.closeOverlay();
        } else {
          this.showOfflineOverlayAndPoll();
        }
      });
    });
  }

  /** Used during application init to load the configuration. */
  public load(): Promise<AppConfig> {
    return firstValueFrom(
      this.getBackendInfo(true).pipe(
        map((bv) => {
          this.config = {
            version: bv.version,
            hostname: bv.name,
            api: environment.apiUrl,
            ws: environment.wsUrl,
            mode: bv.mode
          };
          console.log('API URL set to ' + this.config.api);
          console.log('WS URL set to ' + this.config.ws);
          console.log('Remote reports mode ' + this.config.mode);
          this.isNewGitHubReleaseAvailable$.next(bv.isNewGitHubReleaseAvailable);
          this.isCentral$.next(this.config.mode === MinionMode.CENTRAL);
          this.isManaged$.next(this.config.mode === MinionMode.MANAGED);
          this.isStandalone$.next(this.config.mode === MinionMode.STANDALONE);

          return this.config;
        }),
        switchMap((c) => {
          // trigger load of an existing session in case there is one on the server.
          // finally load authentication setting.
          const loadAuthSettings = this.http.get<WebAuthSettingsDto>(this.config.api + '/master/settings/web-auth', {
            headers: suppressUnauthenticatedDelay(new HttpHeaders())
          });

          const fullHref = globalThis.location.href;
          const urlParams = new URLSearchParams(fullHref.substring(fullHref.indexOf('?') + 1));
          const otp = urlParams.get('otp');
          if (otp) {
            globalThis.history.replaceState({}, document.title, globalThis.location.pathname);
          }
          return combineLatest([of(c), loadAuthSettings, this.loadSession(otp)]);
        }),
        map(([config, authSettings, session]) => {
          this.webAuthCfg = authSettings;
          this.initialSession.next(session);

          // auth0 config.
          if (authSettings?.auth0?.enabled) {
            this.auth0.set({
              clientId: authSettings.auth0.clientId,
              domain: authSettings.auth0.domain
            });
          } else {
            // avoid problems (crash) in auth0 servive.
            this.auth0.set({
              clientId: '',
              domain: ''
            });
          }

          return config;
        })
      )
    );
  }

  public loadSession(oneTimePassword: string): Observable<string | null> {
    const params = oneTimePassword
      ? {
          otp: oneTimePassword
        }
      : null;
    return this.http
      .get(`${this.config.api}/auth/session`, {
        params: params,
        responseType: 'text',
        headers: suppressGlobalErrorHandling(suppressUnauthenticatedDelay(new HttpHeaders()))
      })
      .pipe(
        catchError((err) => {
          console.log(`No existing session on the remote: ${err}`);
          return of(null);
        })
      );
  }

  /** Check whether there is a new version running on the backend, show dialog if it is. */
  public checkServerVersion() {
    this.getBackendInfo(true).subscribe((bv) => {
      this.isNewGitHubReleaseAvailable$.next(bv.isNewGitHubReleaseAvailable);
      this.doCheckVersion(bv);
    });
  }

  private doCheckVersion(bv: BackendInfoDto) {
    if (!isEqual(this.config.version, bv.version)) {
      // offline$ should have already been set to false, thus offline overlay is closed.
      if (this.overlayRef) {
        return; // we're already showing the new version overlay.
      }

      this.ngZone.run(() => {
        // there is no return from here anyway. The user must reload the application.
        this.stopCheckAndLockVersion();

        this.overlayRef = this.overlay.create({
          positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
          hasBackdrop: true
        });

        // create a portal with a custom injector which passes the received version data to show it.
        const portal = new ComponentPortal(
          ConnectionVersionComponent,
          null,
          Injector.create({
            providers: [
              {
                provide: VERSION_DATA,
                useValue: {
                  oldVersion: this.config.version,
                  newVersion: bv.version
                }
              }
            ]
          })
        );
        this.overlayRef.attach(portal);
      });
    }
  }

  /** Stops the server version check. This should be used if we *expect* (and handle) a changing server version, e.g. update. */
  private stopCheckAndLockVersion() {
    clearInterval(this.checkInterval);
    this.versionLock = true;
  }

  /** Call in case of suspected problems with the backend connection, will show a dialog until server connection is restored. */
  public markServerOffline(): void {
    this.offline$.next(true);
  }

  private showOfflineOverlayAndPoll(): void {
    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
      hasBackdrop: true
    });

    const portal = new ComponentPortal(ConnectionLostComponent);
    this.overlayRef.attach(portal);

    this.getBackendInfo()
      .pipe(retry({ delay: 2000 }))
      .subscribe((backendInfoDto) => {
        if (this.versionLock) {
          return;
        }

        if (!this.config) {
          globalThis.location.reload();
        } else {
          this.doCheckVersion(backendInfoDto);
        }
      });
  }

  /** Closes the overlay (connection lost, server version) if present */
  private closeOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

  /** The base URL for a given plugin */
  public getPluginUrl(plugin: PluginInfoDto) {
    return `${this.config.api}/plugins/${plugin.id.id}`;
  }

  /** Tries to fetch the current server version, suppresses global error handling */
  public getBackendInfo(errorHandling = false): Observable<BackendInfoDto> {
    return this.http
      .get<BackendInfoDto>(environment.apiUrl + '/backend-info/version', {
        headers: suppressUnauthenticatedDelay(
          errorHandling ? new HttpHeaders() : suppressGlobalErrorHandling(new HttpHeaders())
        ),
        context: NO_LOADING_BAR_CONTEXT
      })
      .pipe(
        catchError((e) => {
          if (e?.status === 404) {
            // in case the version backend is no longer available, it is extremely likely
            // that the server has been migrated to NODE (nodes don't have UI backends).
            globalThis.location.reload();
          }
          throw e;
        }),
        tap((backendInfoDto) => {
          // calculate the time offset between the client and the server
          const serverTime = backendInfoDto.time;
          const clientTime = Date.now();
          this.backendTimeOffset = serverTime - clientTime;

          // log if we're exceeding a certain threshold
          if (this.backendTimeOffset > 500 && !this.backendOffsetWarning) {
            console.warn('Server time offset', this.backendTimeOffset, 'ms');
            this.backendOffsetWarning = true;
          }

          // force a reload if the server was restarted
          const oldBackendSessionId = this.backendSessionId;
          const newBackendSessionId = backendInfoDto.sessionId;
          this.backendSessionId = newBackendSessionId;
          if (
            oldBackendSessionId !== ConfigService.defaultBackendSessionId &&
            oldBackendSessionId !== newBackendSessionId
          ) {
            console.info('SessionID changed!');
            this.markServerOffline();
            return;
          }

          // however we ended here, we're not offline anymore in case we were!
          this.offline$.next(false);
        })
      );
  }

  public getCorrectedNow(): number {
    return Date.now() + this.backendTimeOffset;
  }
}
