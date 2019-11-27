import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HiveEntryDto } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { LoggingService } from '../../core/services/logging.service';

@Injectable({
  providedIn: 'root',
})
export class HiveService {
  private log = this.loggingService.getLogger('HiveService');

  private backClicked = false;

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) {}

  public getBackClicked(): boolean {
    return this.backClicked;
  }

  public setBackClicked(backClicked: boolean): void {
    this.backClicked = backClicked;
  }

  public listHives(): Observable<string[]> {
    const url: string = this.cfg.config.api + '/hive/listHives';
    return this.http.get<string[]>(url);
  }

  public listManifests(hive: string): Observable<HiveEntryDto[]> {
    const url: string = this.cfg.config.api + '/hive/listManifests';
    const options = { params: new HttpParams().set('hive', hive) };
    return this.http.get<HiveEntryDto[]>(url, options);
  }

  public listManifest(hive: string, name: string, tag: string): Observable<HiveEntryDto[]> {
    const url: string = this.cfg.config.api + '/hive/listManifest';
    const options = {
      params: new HttpParams()
        .set('hive', hive)
        .set('name', name)
        .set('tag', tag),
    };
    return this.http.get<HiveEntryDto[]>(url, options);
  }

  public list(hive: string, id: string): Observable<HiveEntryDto[]> {
    const url: string = this.cfg.config.api + '/hive/list';
    const options = {
      params: new HttpParams().set('hive', hive).set('id', id),
    };
    return this.http.get<HiveEntryDto[]>(url, options);
  }

  public downloadManifest(hive: string, name: string, tag: string) {
    this.log.debug('downloadManifest(' + hive + ', ' + name + ', ' + tag + ')');
    const url: string = this.cfg.config.api + '/hive/downloadManifest';
    const params: HttpParams = new HttpParams()
      .set('hive', hive)
      .set('name', name)
      .set('tag', tag);
    return this.http.get(url, { params: params, responseType: 'blob' });
  }

  public download(hive: string, id: string) {
    this.log.debug('download(' + hive + ', ' + id + ')');
    const url: string = this.cfg.config.api + '/hive/download';
    const params: HttpParams = new HttpParams().set('hive', hive).set('id', id);
    return this.http.get(url, { params: params, responseType: 'blob' });
  }

  public prune(hive: string) {
    this.log.debug(`prune(${hive})`);
    const url: string = this.cfg.config.api + '/hive/prune';
    const params: HttpParams = new HttpParams().set('hive', hive);
    return this.http.get(url, {
      params: params,
      responseType: 'text',
    });
  }

  public delete(hive: string, name: string, tag: string) {
    this.log.debug(`delete(${hive}, ${name}, ${tag})`);
    const url: string = this.cfg.config.api + '/hive/delete';
    const params: HttpParams = new HttpParams()
      .set('hive', hive)
      .set('name', name)
      .set('tag', tag);
    return this.http.delete(url, { params: params });
  }

  public fsck(hive: string, fix: boolean) {
    this.log.debug(`fsck(${hive})`);
    const url: string = this.cfg.config.api + '/hive/fsck';
    const params: HttpParams = new HttpParams().set('hive', hive).set('fix', fix.toString());
    return this.http.get<Map<string, string>>(url, {
      params: params,
    });
  }
}
