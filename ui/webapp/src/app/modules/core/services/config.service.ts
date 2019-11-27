import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { BackendInfoDto, MinionMode, MinionStatusDto } from '../../../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';
import { LoggingService, LogLevel } from './logging.service';

export interface AppConfig {
  api: string;
  logLevel: LogLevel;
  mode: MinionMode;
}

@Injectable({
  providedIn: 'root',
})
export class ConfigService {
  config: AppConfig;

  constructor(
    private http: HttpClient,
    private loggingService: LoggingService,
    iconRegistry: MatIconRegistry,
    sanitizer: DomSanitizer,
  ) {
    iconRegistry.addSvgIcon('bdeploy', sanitizer.bypassSecurityTrustResourceUrl('assets/logo-single-path-square.svg'));
    iconRegistry.addSvgIcon('progress', sanitizer.bypassSecurityTrustResourceUrl('assets/progress.svg'));
    iconRegistry.addSvgIcon('plus', sanitizer.bypassSecurityTrustResourceUrl('assets/plus.svg'));
    iconRegistry.addSvgIcon('star', sanitizer.bypassSecurityTrustResourceUrl('assets/star.svg'));
    iconRegistry.addSvgIcon('LINUX', sanitizer.bypassSecurityTrustResourceUrl('assets/linux.svg'));
    iconRegistry.addSvgIcon('WINDOWS', sanitizer.bypassSecurityTrustResourceUrl('assets/windows.svg'));
    iconRegistry.addSvgIcon('AIX', sanitizer.bypassSecurityTrustResourceUrl('assets/aix.svg'));
    iconRegistry.addSvgIcon('MACOS', sanitizer.bypassSecurityTrustResourceUrl('assets/mac.svg'));
  }

  load(): Promise<AppConfig> {
    return new Promise(resolve => {
      this.getBackendVersion().subscribe((bv) => {
        this.config = {
          api: environment.apiUrl,
          logLevel: environment.logLevel,
          mode: bv.mode
        };
        this.loggingService.getLogger(null).setLogLevel(this.config.logLevel);
        this.loggingService.getLogger(null).info('API URL set to ' + this.config.api);
        this.loggingService.getLogger(null).info('Remote reports mode ' + this.config.mode);
        resolve(this.config);
      });
    });
  }

  public getBackendVersion(): Observable<BackendInfoDto> {
    // use environment instead of config here...
    return this.http.get<BackendInfoDto>(environment.apiUrl + '/backend-info/version');
  }

  public tryGetBackendVersion(): Observable<BackendInfoDto> {
    return this.http.get<BackendInfoDto>(environment.apiUrl + '/backend-info/version', { headers: suppressGlobalErrorHandling(new HttpHeaders)});
  }

  public getNodeStates() {
    return this.http.get<{ [minionName: string]: MinionStatusDto}[]>(environment.apiUrl + '/backend-info/minion-status');
  }

}
