import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryDetailsService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);

  private readonly apiPath = (r: string) => `${this.cfg.config.api}/softwarerepository/${r}`;

  public delete(repository: SoftwareRepositoryConfiguration): Observable<unknown> {
    return this.http.delete(`${this.apiPath(repository.name)}`);
  }

  public update(repository: SoftwareRepositoryConfiguration): Observable<unknown> {
    return this.http.post(this.apiPath(repository.name), repository);
  }
}
