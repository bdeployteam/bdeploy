import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CustomEditor, ManifestKey, PluginInfoDto } from 'src/app/models/gen.dtos';
import { ConfigService } from './config.service';

export interface EditorPlugin {
  new(api: Api);

  // onRead = plugin reads value to be edited (initially).
  // onUpdate = called when plugin updates the valued.
  bind(onRead: () => string, onUpdate: (value: string) => void, onValidStateChange: (valid: boolean) => void): HTMLElement;
}

// the Api interface is passed to the plugins constructor. it holds callbacks which can perform API calls to the plugin's server side.
// this way the plugin does not need to know how to perform REST calls at all and can be independent of angular, etc.
export interface Api {
  get(path: string, params?: {[key: string]: string}): Promise<any>;
  put(path: string, body: any, params?: {[key: string]: string}): Promise<any>;
  post(path: string, body: any, params?: {[key: string]: string}): Promise<any>;
  delete(path: string, params?: {[key: string]: string}): Promise<any>;
  getResourceUrl(): string;
}

@Injectable({
  providedIn: 'root'
})
export class PluginService {

  constructor(private http: HttpClient, private config: ConfigService) { }

  public getEditorPlugin(group: string, product: ManifestKey, editorType: string): Observable<PluginInfoDto> {
    const url = this.config.config.api + '/plugin-admin/get-editor/' + group + '/' + editorType;
    return this.http.post<PluginInfoDto>(url, product);
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
