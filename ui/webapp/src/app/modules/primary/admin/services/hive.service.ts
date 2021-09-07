import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { HiveEntryDto } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class HiveService {
  public loading$ = new BehaviorSubject<boolean>(false);

  private apiPath = () => `${this.cfg.config.api}/hive`;

  constructor(private cfg: ConfigService, private http: HttpClient) {}

  public listHives(): Observable<string[]> {
    this.loading$.next(true);
    return this.http.get<string[]>(`${this.apiPath()}/listHives`).pipe(
      measure('List BHives'),
      finalize(() => this.loading$.next(false))
    );
  }

  public listManifests(hive: string): Observable<HiveEntryDto[]> {
    this.loading$.next(true);
    const options = { params: new HttpParams().set('hive', hive) };
    return this.http.get<HiveEntryDto[]>(`${this.apiPath()}/listManifests`, options).pipe(
      measure('List BHive Manifests'),
      finalize(() => this.loading$.next(false))
    );
  }

  public listManifest(hive: string, name: string, tag: string): Observable<HiveEntryDto[]> {
    this.loading$.next(true);
    const options = {
      params: new HttpParams().set('hive', hive).set('name', name).set('tag', tag),
    };
    return this.http.get<HiveEntryDto[]>(`${this.apiPath()}/listManifest`, options).pipe(
      measure('List BHive Manifest Content'),
      finalize(() => this.loading$.next(false))
    );
  }

  public list(hive: string, id: string): Observable<HiveEntryDto[]> {
    this.loading$.next(true);
    const options = {
      params: new HttpParams().set('hive', hive).set('id', id),
    };
    return this.http.get<HiveEntryDto[]>(`${this.apiPath()}/list`, options).pipe(
      measure('List BHive Tree'),
      finalize(() => this.loading$.next(false))
    );
  }

  public download(hive: string, id: string) {
    this.loading$.next(true);
    const params: HttpParams = new HttpParams().set('hive', hive).set('id', id);
    return this.http.get(`${this.apiPath()}/download`, { params: params, responseType: 'blob' }).pipe(finalize(() => this.loading$.next(false)));
  }

  public prune(hive: string): Observable<string> {
    const params: HttpParams = new HttpParams().set('hive', hive);
    return this.http.get(`${this.apiPath()}/prune`, {
      params: params,
      responseType: 'text',
    });
  }

  public delete(hive: string, name: string, tag: string) {
    const params: HttpParams = new HttpParams().set('hive', hive).set('name', name).set('tag', tag);
    return this.http.delete(`${this.apiPath()}/delete`, { params: params });
  }

  public fsck(hive: string, fix: boolean): Observable<Map<string, string>> {
    const params: HttpParams = new HttpParams().set('hive', hive).set('fix', fix.toString());
    return this.http.get<Map<string, string>>(`${this.apiPath()}/fsck`, {
      params: params,
    });
  }
}
