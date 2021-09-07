import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { AuditLogDto } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class AuditService {
  public loading$ = new BehaviorSubject<boolean>(false);
  private apiPath = () => `${this.cfg.config.api}/audit`;

  constructor(private cfg: ConfigService, private http: HttpClient) {}

  public hiveAuditLog(hive: string, lastInstant: number, lineLimit: number): Observable<AuditLogDto[]> {
    this.loading$.next(true);
    let params: HttpParams = new HttpParams().set('hive', hive).set('lineLimit', '' + lineLimit);
    if (lastInstant) {
      params = params.set('lastInstant', '' + lastInstant);
    }
    return this.http.get<AuditLogDto[]>(`${this.apiPath()}/hiveAuditLog`, { params: params }).pipe(
      measure('Load BHive Audit Logs'),
      finalize(() => this.loading$.next(false))
    );
  }
}
