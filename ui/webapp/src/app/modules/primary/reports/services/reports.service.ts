import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable, tap } from 'rxjs';
import { ReportDescriptor, ReportRequestDto, ReportResponseDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';

@Injectable({
  providedIn: 'root',
})
export class ReportsService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly nav = inject(NavAreasService);

  private readonly apiPath = `${this.cfg.config.api}/report`;

  /** All reports */
  public reports$ = new BehaviorSubject<ReportDescriptor[]>([]);

  /** Current report */
  public current$ = new BehaviorSubject<ReportDescriptor>(null);

  /** Last requested report data */
  public generatedReport$ = new BehaviorSubject<ReportResponseDto>(null);

  constructor() {
    this.http.get<ReportDescriptor[]>(this.apiPath).subscribe((reports) => this.reports$.next(reports));
    combineLatest([this.nav.panelRoute$, this.reports$]).subscribe(([panelRoute, reports]) => {
      const reportType = panelRoute?.params?.['report'];
      if (!reportType || !reports) {
        this.current$.next(null);
      }
      const desc = reports.find((r) => r.type === reportType);
      if (this.current$.value !== desc) {
        this.current$.next(desc);
      }
    });
  }

  public generateReport(request: ReportRequestDto): Observable<ReportResponseDto> {
    return this.http
      .post<ReportResponseDto>(`${this.apiPath}/${this.current$.value.type}`, request)
      .pipe(tap((dto) => this.generatedReport$.next(dto)));
  }
}
