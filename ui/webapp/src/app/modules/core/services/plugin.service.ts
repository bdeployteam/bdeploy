import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { lastValueFrom, Observable } from 'rxjs';
import { ManifestKey, PluginInfoDto } from 'src/app/models/gen.dtos';
import { Api } from '../plugins/plugin.api';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root',
})
export class PluginService {
  constructor(private http: HttpClient, private config: ConfigService) {}

  public getEditorPlugin(
    group: string,
    product: ManifestKey,
    editorType: string
  ): Observable<PluginInfoDto> {
    const url =
      this.config.config.api +
      '/plugin-admin/get-editor/' +
      group +
      '/' +
      editorType;
    return this.http.post<PluginInfoDto>(url, product, {
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  public load(plugin: PluginInfoDto, modulePath: string): Promise<any> {
    // Note: webpackIgnore is extremely important, otherwise webpack tries to resolve the import locally at build time.
    return import(
      /* webpackIgnore: true */ this.config.getPluginUrl(plugin) + modulePath
    );
  }

  private buildPluginUrl(plugin: PluginInfoDto, path: string): string {
    return (
      this.config.getPluginUrl(plugin) +
      (path.startsWith('/') ? path : '/' + path)
    );
  }

  public getApi(plugin: PluginInfoDto): Api {
    return {
      get(path, params?) {
        return lastValueFrom(
          this.http.get(this.buildPluginUrl(plugin, path), {
            params: params,
            responseType: 'text',
          })
        );
      },
      put(path, body, params?) {
        return lastValueFrom(
          this.http.put(this.buildPluginUrl(plugin, path), body, {
            params: params,
            responseType: 'text',
          })
        );
      },
      post(path, body, params?) {
        return lastValueFrom(
          this.http.post(this.buildPluginUrl(plugin, path), body, {
            params: params,
            responseType: 'text',
          })
        );
      },
      delete(path, params?) {
        return lastValueFrom(
          this.http.delete(this.buildPluginUrl(plugin, path), {
            params: params,
            responseType: 'text',
          })
        );
      },
      getResourceUrl() {
        return this.config.getPluginUrl(plugin);
      },
    };
  }
}
