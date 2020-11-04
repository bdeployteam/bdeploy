import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { JerseyServerMonitoringDto } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class MetricsService {
  constructor(private http: HttpClient, private config: ConfigService) {}

  public getAllMetrics(): Observable<any> {
    return this.http.get<any>(this.config.config.api + '/metrics');
  }

  public getServerMetrics(): Observable<JerseyServerMonitoringDto> {
    return this.http.get<any>(this.config.config.api + '/server-monitor');
  }
}
