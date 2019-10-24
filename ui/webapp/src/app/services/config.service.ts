import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { AppConfig } from '../models/config.model';
import { AttachIdentDto, BackendInfoDto, InstanceConfiguration, NodeStatus } from '../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
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

  public getAttachIdent(): Observable<AttachIdentDto> {
    return this.http.get<AttachIdentDto>(environment.apiUrl + '/backend-info/attach-ident');
  }

  public tryAutoAttach(group: string, ident: AttachIdentDto): Observable<any> {
    return this.http.put(environment.apiUrl + '/local-servers/auto-attach/' + group, ident, { headers: suppressGlobalErrorHandling(new HttpHeaders)});
  }

  public manualAttach(group: string, ident: AttachIdentDto): Observable<any> {
    return this.http.put(environment.apiUrl + '/local-servers/manual-attach/' + group, ident);
  }

  public manualAttachCentral(ident: string): Observable<string> {
    return this.http.put(environment.apiUrl + '/local-servers/manual-attach-central', ident, {responseType: 'text'});
  }

  public getCentralIdent(group: string, ident: AttachIdentDto): Observable<string> {
    return this.http.post(environment.apiUrl + '/local-servers/central-ident/' + group, ident, { responseType: 'text' });
  }

  public getLocalServers(group: string): Observable<AttachIdentDto[]> {
    return this.http.get<AttachIdentDto[]>(environment.apiUrl + '/local-servers/list/' + group);
  }

  public getServerForInstance(group: string, instance: string, tag: string): Observable<AttachIdentDto> {
    const p = new HttpParams();
    if (tag) {
      p.set('instanceTag', tag);
    }
    return this.http.get<AttachIdentDto>(environment.apiUrl + '/local-servers/controlling-server/' + group + '/' + instance, {params: p});
  }

  public getInstancesForServer(group: string, server: string): Observable<InstanceConfiguration[]> {
    return this.http.get<InstanceConfiguration[]>(environment.apiUrl + '/local-servers/controlled-instances/' + group + '/' + server);
  }

  public deleteLocalServer(group: string, server: string): Observable<any> {
    return this.http.post(environment.apiUrl + '/local-servers/delete-server/' + group + '/' + server, server);
  }

  public minionsOfLocalServer(group: string, server: string): Observable<{[key: string]: NodeStatus}> {
    return this.http.get<{[key: string]: NodeStatus}>(environment.apiUrl + '/local-servers/minions/' + group + '/' + server);
  }
}
