import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { convert2String } from 'src/app/modules/shared/utils/version.utils';
import { environment } from 'src/environments/environment';
import {
  BackendInfoDto,
  MinionMode,
  MinionStatusDto,
  PluginInfoDto,
  Version
} from '../../../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';
import { LoggingService, LogLevel } from './logging.service';

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
  config: AppConfig;
  newVersionInterval;

  constructor(
    private http: HttpClient,
    private loggingService: LoggingService,
    iconRegistry: MatIconRegistry,
    sanitizer: DomSanitizer,
    private mbService: MessageboxService
  ) {
    iconRegistry.addSvgIcon(
      'bdeploy',
      sanitizer.bypassSecurityTrustResourceUrl(
        'assets/logo-single-path-square.svg'
      )
    );
    iconRegistry.addSvgIcon(
      'progress',
      sanitizer.bypassSecurityTrustResourceUrl('assets/progress.svg')
    );
    iconRegistry.addSvgIcon(
      'plus',
      sanitizer.bypassSecurityTrustResourceUrl('assets/plus.svg')
    );
    iconRegistry.addSvgIcon(
      'star',
      sanitizer.bypassSecurityTrustResourceUrl('assets/star.svg')
    );
    iconRegistry.addSvgIcon(
      'LINUX',
      sanitizer.bypassSecurityTrustResourceUrl('assets/linux.svg')
    );
    iconRegistry.addSvgIcon(
      'WINDOWS',
      sanitizer.bypassSecurityTrustResourceUrl('assets/windows.svg')
    );
    iconRegistry.addSvgIcon(
      'AIX',
      sanitizer.bypassSecurityTrustResourceUrl('assets/aix.svg')
    );
    iconRegistry.addSvgIcon(
      'MACOS',
      sanitizer.bypassSecurityTrustResourceUrl('assets/mac.svg')
    );
    setInterval(() => this.isNewVersionAvailable(), 60000);
    this.newVersionInterval = setInterval(
      () => this.isNewVersionAvailable(),
      60000
    );
  }

  load(): Promise<AppConfig> {
    return new Promise((resolve) => {
      this.getBackendInfo().subscribe((bv) => {
        this.config = {
          version: bv.version,
          api: environment.apiUrl,
          logLevel: environment.logLevel,
          mode: bv.mode,
        };
        this.loggingService.getLogger(null).setLogLevel(this.config.logLevel);
        this.loggingService
          .getLogger(null)
          .info('API URL set to ' + this.config.api);
        this.loggingService
          .getLogger(null)
          .info('Remote reports mode ' + this.config.mode);
        resolve(this.config);
      });
    });
  }

  public isNewVersionAvailable() {
    this.getBackendInfo().subscribe((bv) => {
      const currentVersion = convert2String(this.config.version);
      const newVersion = convert2String(bv.version);

      if (currentVersion !== newVersion) {
        this.loggingService
          .getLogger(null)
          .info(
            'A new version is available! old: ' +
              currentVersion +
              ' | new: ' +
              newVersion
          );

        this.mbService
          .open({
            title: 'New Version',
            message:
              'A software update has been installed on the server. The page needs to be re-loaded to continue working.',
            mode: MessageBoxMode.INFO,
          })
          .subscribe((r) => {
            window.location.reload();
          });
      }
    });
  }

  public stopNewVersionInterval() {
    clearInterval(this.newVersionInterval);
  }

  public getWsUrl(): string {
    if (this.config.api.startsWith('https://')) {
      return this.config.api.replace('https', 'wss').replace('/api', '/ws');
    } else if (this.config.api.startsWith('/')) {
      // relative, use browser information to figure out an absolute URL, since WebSockets require this.
      const url = new URL(window.location.href);
      return 'wss://' + url.host + '/ws';
    } else {
      throw new Error('Cannot figure out WebSocket URL');
    }
  }

  public getPluginUrl(plugin: PluginInfoDto) {
    return this.config.api + '/plugins/' + plugin.id.id;
  }

  public getBackendInfo(): Observable<BackendInfoDto> {
    return this.http.get<BackendInfoDto>(
      environment.apiUrl + '/backend-info/version'
    );
  }

  public tryGetBackendInfo(): Observable<BackendInfoDto> {
    return this.http.get<BackendInfoDto>(
      environment.apiUrl + '/backend-info/version',
      { headers: suppressGlobalErrorHandling(new HttpHeaders()) }
    );
  }

  public getNodeStates() {
    return this.http.get<{ [minionName: string]: MinionStatusDto }[]>(
      environment.apiUrl + '/backend-info/minion-status'
    );
  }
}
