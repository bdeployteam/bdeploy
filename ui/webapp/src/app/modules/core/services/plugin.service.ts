import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CustomEditor, ManifestKey, PluginInfoDto } from 'src/app/models/gen.dtos';
import { Api } from '../../shared/plugins/plugin.api';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class PluginService {

  constructor(private http: HttpClient, private config: ConfigService) { }

  public getEditorPlugin(group: string, product: ManifestKey, editorType: string): Observable<PluginInfoDto> {
    const url = this.config.config.api + '/plugin-admin/get-editor/' + group + '/' + editorType;
    return this.http.post<PluginInfoDto>(url, product, { headers: suppressGlobalErrorHandling(new HttpHeaders) });
  }

  private buildPluginUrl(plugin: PluginInfoDto, path: string): string {
    return this.config.getPluginUrl(plugin) + (path.startsWith('/') ? path : ('/' + path));
  }

  public load(plugin: PluginInfoDto, editor: CustomEditor): Promise<any> {
    // Note: webpackIgnore is extremely important, otherwise webpack tries to resolve the import locally at build time.
    return import(/* webpackIgnore: true */ this.config.getPluginUrl(plugin) + editor.modulePath);
  }

  public getApi(plugin: PluginInfoDto): Api {
    const delegate = this;
    return {
      get(path, params?) { return delegate.http.get(delegate.buildPluginUrl(plugin, path), {params: params, responseType: 'text'}).toPromise(); },
      put(path, body, params?) { return delegate.http.put(delegate.buildPluginUrl(plugin, path), body, {params: params, responseType: 'text'}).toPromise(); },
      post(path, body, params?) { return delegate.http.post(delegate.buildPluginUrl(plugin, path), body, {params: params, responseType: 'text'}).toPromise(); },
      delete(path, params?) { return delegate.http.delete(delegate.buildPluginUrl(plugin, path), {params: params, responseType: 'text'}).toPromise(); },
      getResourceUrl() { return delegate.config.getPluginUrl(plugin); }
    };
  }

}
