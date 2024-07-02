import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { JerseyServerMonitoringDto } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class MetricsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  public getAllMetrics(): Observable<unknown> {
    return this.http.get<unknown>(this.config.config.api + '/metrics');
  }

  public getServerMetrics(): Observable<JerseyServerMonitoringDto> {
    return this.http.get<any>(this.config.config.api + '/server-monitor');
  }
}
