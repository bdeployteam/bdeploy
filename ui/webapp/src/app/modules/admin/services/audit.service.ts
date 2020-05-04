import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditLogDto } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { LoggingService } from '../../core/services/logging.service';

@Injectable({
  providedIn: 'root',
})
export class AuditService {
  private log = this.loggingService.getLogger('AuditService');

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) {}

  public hiveAuditLog(hive: string, lastInstant: number, lineLimit: number): Observable<AuditLogDto[]> {
    const url: string = this.cfg.config.api + '/audit/hiveAuditLog';
    let params: HttpParams = new HttpParams().set('hive', hive).set('lineLimit', '' + lineLimit);
    if (lastInstant) {
      params = params.set('lastInstant', '' + lastInstant);
    }
    return this.http.get<AuditLogDto[]>(url, {'params': params});
  }

  public auditLog(lastInstant: number, lineLimit: number): Observable<AuditLogDto[]> {
    const url: string = this.cfg.config.api + '/audit/auditLog';
    let params: HttpParams = new HttpParams().set('lineLimit', '' + lineLimit);
    if (lastInstant) {
      params = params.set('lastInstant', '' + lastInstant);
    }
    return this.http.get<AuditLogDto[]>(url, {'params': params});
  }

}
