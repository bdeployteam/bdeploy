import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { JerseyServerMonitoringDto, MetricBundle, MetricGroup } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class MetricsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  public getAllMetrics(): Observable<Record<MetricGroup, MetricBundle>> {
    return this.http.get<Record<MetricGroup, MetricBundle>>(this.config.config.api + '/metrics');
  }

  public getServerMetrics(): Observable<JerseyServerMonitoringDto> {
    return this.http.get<JerseyServerMonitoringDto>(this.config.config.api + '/server-monitor');
  }
}
