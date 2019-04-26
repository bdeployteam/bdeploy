import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatIconRegistry } from '@angular/material';
import { DomSanitizer } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { AppConfig } from '../models/config.model';
import { LoggingService } from './logging.service';

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
    iconRegistry.addSvgIcon('bee', sanitizer.bypassSecurityTrustResourceUrl('assets/bee.svg'));
    iconRegistry.addSvgIcon('bdeploy', sanitizer.bypassSecurityTrustResourceUrl('assets/logo-single-path-square.svg'));
    iconRegistry.addSvgIcon('progress', sanitizer.bypassSecurityTrustResourceUrl('assets/progress.svg'));
    iconRegistry.addSvgIcon('LINUX', sanitizer.bypassSecurityTrustResourceUrl('assets/linux.svg'));
    iconRegistry.addSvgIcon('WINDOWS', sanitizer.bypassSecurityTrustResourceUrl('assets/windows.svg'));
    iconRegistry.addSvgIcon('AIX', sanitizer.bypassSecurityTrustResourceUrl('assets/aix.svg'));
  }

  load(): Promise<AppConfig> {
    const file = 'assets/config.json';
    return new Promise(resolve => {
      this.http.get<AppConfig>(file).subscribe((cfg: AppConfig) => {
        this.config = cfg;
        this.loggingService.getLogger(null).setLogLevel(cfg.logLevel);
        resolve(cfg);
      });
    });
  }

  public getBackendVersion(): Observable<string> {
    return this.http.get(this.config.api + '/backend-info/version', { responseType: 'text' });
  }
}
