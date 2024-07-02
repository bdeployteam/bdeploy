import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { JobDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class JobService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);

  private readonly apiPath = `${this.cfg.config.api}/job`;

  load(): Observable<JobDto[]> {
    return this.http.get<JobDto[]>(`${this.apiPath}/list`);
  }

  run(job: JobDto): Observable<void> {
    return this.http.post<void>(`${this.apiPath}/run`, job);
  }
}
