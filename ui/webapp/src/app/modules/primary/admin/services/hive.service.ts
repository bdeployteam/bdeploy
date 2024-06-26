import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { HiveEntryDto, HiveInfoDto, RepairAndPruneResultDto } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class HiveService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);

  public loading$ = new BehaviorSubject<boolean>(false);
  public hives$ = new BehaviorSubject<HiveInfoDto[]>([]);

  private apiPath = () => `${this.cfg.config.api}/hive`;

  public loadHives() {
    this.loading$.next(true);
    this.http
      .get<HiveInfoDto[]>(`${this.apiPath()}/listHives`)
      .pipe(
        measure('List BHives'),
        finalize(() => this.loading$.next(false)),
      )
      .subscribe((l) => this.hives$.next(l));
  }

  public listManifests(hive: string): Observable<HiveEntryDto[]> {
    this.loading$.next(true);
    const options = { params: new HttpParams().set('hive', hive) };
    return this.http.get<HiveEntryDto[]>(`${this.apiPath()}/listManifests`, options).pipe(
      measure('List BHive Manifests'),
      finalize(() => this.loading$.next(false)),
    );
  }

  public listManifest(hive: string, name: string, tag: string): Observable<HiveEntryDto[]> {
    this.loading$.next(true);
    const options = {
      params: new HttpParams().set('hive', hive).set('name', name).set('tag', tag),
    };
    return this.http.get<HiveEntryDto[]>(`${this.apiPath()}/listManifest`, options).pipe(
      measure('List BHive Manifest Content'),
      finalize(() => this.loading$.next(false)),
    );
  }

  public list(hive: string, id: string): Observable<HiveEntryDto[]> {
    this.loading$.next(true);
    const options = {
      params: new HttpParams().set('hive', hive).set('id', id),
    };
    return this.http.get<HiveEntryDto[]>(`${this.apiPath()}/list`, options).pipe(
      measure('List BHive Tree'),
      finalize(() => this.loading$.next(false)),
    );
  }

  public download(hive: string, id: string) {
    this.loading$.next(true);
    const params: HttpParams = new HttpParams().set('hive', hive).set('id', id);
    return this.http
      .get(`${this.apiPath()}/download`, {
        params: params,
        responseType: 'blob',
      })
      .pipe(finalize(() => this.loading$.next(false)));
  }

  public downloadContent(hive: string, dto: HiveEntryDto) {
    this.loading$.next(true);
    const params: HttpParams = new HttpParams().set('hive', hive);
    return this.http
      .post(`${this.apiPath()}/download`, dto, {
        params: params,
        responseType: 'blob',
      })
      .pipe()
      .pipe(finalize(() => this.loading$.next(false)));
  }

  public delete(hive: string, name: string, tag: string) {
    const params: HttpParams = new HttpParams().set('hive', hive).set('name', name).set('tag', tag);
    return this.http.delete(`${this.apiPath()}/delete`, { params: params }).pipe(finalize(() => this.loadHives()));
  }

  public repairAndPrune(hive: string, fix: boolean): Observable<RepairAndPruneResultDto> {
    const params: HttpParams = new HttpParams().set('hive', hive).set('fix', fix.toString());
    return this.http.get<RepairAndPruneResultDto>(`${this.apiPath()}/repair-and-prune`, {
      params: params,
    });
  }

  public enablePool(hive: string): Observable<unknown> {
    const params = new HttpParams().set('hive', hive);
    return this.http.get(`${this.apiPath()}/pool/enable`, { params }).pipe(finalize(() => this.loadHives()));
  }

  public disablePool(hive: string) {
    const params = new HttpParams().set('hive', hive);
    return this.http.get(`${this.apiPath()}/pool/disable`, { params }).pipe(finalize(() => this.loadHives()));
  }
}
