import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { isEqual } from 'lodash-es';
import { Observable } from 'rxjs';
import { delay, retryWhen } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { BackendInfoDto, MinionMode, MinionStatusDto, PluginInfoDto, Version } from '../../../models/gen.dtos';
import { ConnectionLostComponent } from '../components/connection-lost/connection-lost.component';
import { ConnectionVersionComponent, VERSION_DATA } from '../components/connection-version/connection-version.component';
import { NO_LOADING_BAR_HDRS } from '../utils/loading-bar.util';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
import { LoggingService, LogLevel } from './logging.service';
import { ThemeService } from './theme.service';

export interface AppConfig {
  version: Version;
  api: string;
  logLevel: LogLevel;
  mode: MinionMode;
}

@Injectable({
  providedIn: 'root',
})
export class ConfigService {
  public config: AppConfig;

  private checkInterval;
  private isUnreachable = false;
  private overlayRef: OverlayRef;

  constructor(
    private themes: ThemeService /* dummy: required to bootstrap theming early! */,
    private http: HttpClient,
    private loggingService: LoggingService,
    private overlay: Overlay,
    iconRegistry: MatIconRegistry,
    sanitizer: DomSanitizer
  ) {
    // register all custom icons we want to use with <mat-icon>
    iconRegistry.addSvgIcon('bdeploy', sanitizer.bypassSecurityTrustResourceUrl('assets/logo-single-path-square.svg'));
    iconRegistry.addSvgIcon('progress', sanitizer.bypassSecurityTrustResourceUrl('assets/progress.svg'));
    iconRegistry.addSvgIcon('plus', sanitizer.bypassSecurityTrustResourceUrl('assets/plus.svg'));
    iconRegistry.addSvgIcon('star', sanitizer.bypassSecurityTrustResourceUrl('assets/star.svg'));
    iconRegistry.addSvgIcon('group-settings', sanitizer.bypassSecurityTrustResourceUrl('assets/group-settings.svg'));
    iconRegistry.addSvgIcon('instance-settings', sanitizer.bypassSecurityTrustResourceUrl('assets/instance-settings.svg'));
    iconRegistry.addSvgIcon('repository-settings', sanitizer.bypassSecurityTrustResourceUrl('assets/repository-settings.svg'));

    iconRegistry.addSvgIcon('LINUX', sanitizer.bypassSecurityTrustResourceUrl('assets/linux.svg'));
    iconRegistry.addSvgIcon('WINDOWS', sanitizer.bypassSecurityTrustResourceUrl('assets/windows.svg'));
    iconRegistry.addSvgIcon('AIX', sanitizer.bypassSecurityTrustResourceUrl('assets/aix.svg'));
    iconRegistry.addSvgIcon('MACOS', sanitizer.bypassSecurityTrustResourceUrl('assets/mac.svg'));

    // check whether the server version changed every minute.
    // *usually* we loose the server connection for a short period when this happens, so the interval is just a fallback.
    this.checkInterval = setInterval(() => this.checkServerVersion(), 60000);
  }

  /** Used during application init to load the configuration. */
  public load(): Promise<AppConfig> {
    return new Promise((resolve) => {
      this.getBackendInfo(true).subscribe(
        (bv) => {
          this.config = {
            version: bv.version,
            api: environment.apiUrl,
            logLevel: environment.logLevel,
            mode: bv.mode,
          };
          this.loggingService.getLogger(null).setLogLevel(this.config.logLevel);
          this.loggingService.getLogger(null).info('API URL set to ' + this.config.api);
          this.loggingService.getLogger(null).info('Remote reports mode ' + this.config.mode);
          resolve(this.config);
        },
        (err) => {
          this.loggingService.getLogger(null).errorWithGuiMessage(err);
        }
      );
    });
  }

  /** Check whether there is a new version running on the backend, show dialog if it is. */
  public checkServerVersion() {
    this.getBackendInfo(true).subscribe((bv) => {
      this.doCheckVersion(bv);
    });
  }

  private doCheckVersion(bv: BackendInfoDto) {
    if (!isEqual(this.config.version, bv.version)) {
      if (!!this.overlayRef) {
        if (this.isUnreachable) {
          // we were recovering and now the backend reports another version.
          this.closeOverlay();
        } else {
          return; // we're already showing the new version overlay.
        }
      }

      // there is no return from here anyway. The user must reload the application.
      this.stopCheckServerVersion();

      this.overlayRef = this.overlay.create({
        positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
        hasBackdrop: true,
      });

      // create a portal with a custom injector which passes the received version data to show it.
      const portal = new ComponentPortal(
        ConnectionVersionComponent,
        null,
        Injector.create({ providers: [{ provide: VERSION_DATA, useValue: { oldVersion: this.config.version, newVersion: bv.version } }] })
      );
      this.overlayRef.attach(portal);
    }
  }

  /** Stops the server version check. This should be used if we *expect* (and handle) a changing server version, e.g. update. */
  public stopCheckServerVersion() {
    clearInterval(this.checkInterval);
  }

  /** Call in case of suspected problems with the backend connection, will show a dialog until server connection is restored. */
  public checkServerReachable(): void {
    if (!this.isUnreachable) {
      this.isUnreachable = true;

      this.overlayRef = this.overlay.create({
        positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
        hasBackdrop: true,
      });

      const portal = new ComponentPortal(ConnectionLostComponent);
      this.overlayRef.attach(portal);

      this.getBackendInfo()
        .pipe(retryWhen((errors) => errors.pipe(delay(2000))))
        .subscribe((r) => {
          if (!this.config) {
            window.location.reload();
          } else {
            this.doCheckVersion(r);
          }

          this.closeOverlay();
          this.isUnreachable = false;
        });
    }
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
    return this.config.api + '/plugins/' + plugin.id.id;
  }

  /** Tries to fetch the current server version, suppresses global error handling */
  public getBackendInfo(errorHandling = false): Observable<BackendInfoDto> {
    return this.http.get<BackendInfoDto>(environment.apiUrl + '/backend-info/version', {
      headers: errorHandling ? NO_LOADING_BAR_HDRS : suppressGlobalErrorHandling(new HttpHeaders(NO_LOADING_BAR_HDRS)),
    });
  }

  /** Fetches node states - use when applying updates. */
  public getNodeStates() {
    return this.http.get<{ [minionName: string]: MinionStatusDto }[]>(environment.apiUrl + '/backend-info/minion-status');
  }

  /** Determines whether the server is a 'CENTRAL' mode server */
  public isCentral(): boolean {
    return this.config.mode === MinionMode.CENTRAL;
  }

  /** Determines whether the server is a 'MANAGED' mode server */
  public isManaged(): boolean {
    return this.config.mode === MinionMode.MANAGED;
  }

  /** Determines whether the server is a 'STANDALONE' mode server */
  public isStandalone(): boolean {
    return this.config.mode === MinionMode.STANDALONE;
  }
}
