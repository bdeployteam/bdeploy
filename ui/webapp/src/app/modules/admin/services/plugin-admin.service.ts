import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ManifestKey, PluginInfoDto } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { Logger, LoggingService } from '../../core/services/logging.service';

@Injectable({
  providedIn: 'root',
})
export class PluginAdminService {
  private static BASEPATH = '/plugin-admin';
  private log: Logger = this.loggingService.getLogger('PluginService');

  constructor(private http: HttpClient, private cfg: ConfigService, private loggingService: LoggingService) {}

  public getAll(): Observable<PluginInfoDto[]> {
    const url: string = this.cfg.config.api + PluginAdminService.BASEPATH + '/list';
    this.log.debug('getAll: ' + url);
    return this.http.get<PluginInfoDto[]>(url);
  }

  public getProductPlugins(group: string, product: ManifestKey): Observable<PluginInfoDto[]> {
    const url: string = this.cfg.config.api + PluginAdminService.BASEPATH + '/list-product-plugins/' + group;
    this.log.debug('getProductPlugins: ' + url);
    return this.http.post<PluginInfoDto[]>(url, product);
  }

  public loadGlobalPlugin(dto: PluginInfoDto) {
    const url: string = this.cfg.config.api + PluginAdminService.BASEPATH + '/load-global';
    this.log.debug('loadGlobalPlugin: ' + url);
    return this.http.post(url, dto.id);
  }

  public unloadPlugin(dto: PluginInfoDto) {
    const url: string = this.cfg.config.api + PluginAdminService.BASEPATH + '/unload';
    this.log.debug('unloadPlugin: ' + url);
    return this.http.post(url, dto.id);
  }

  public getGlobalUploadUrl(): string {
    return this.cfg.config.api + PluginAdminService.BASEPATH + '/upload-global';
  }

  public deleteGlobalPlugin(dto: PluginInfoDto) {
    const url: string = this.cfg.config.api + PluginAdminService.BASEPATH + '/delete-global';
    this.log.debug('deleteGlobalPlugin: ' + url);
    return this.http.post(url, dto.id);
  }
}
