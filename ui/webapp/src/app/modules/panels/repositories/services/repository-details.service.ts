import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryDetailsService {
  private apiPath = (r) => `${this.cfg.config.api}/softwarerepository/${r}`;

  constructor(private cfg: ConfigService, private http: HttpClient, private repositories: RepositoriesService) {}

  public delete(repository: SoftwareRepositoryConfiguration): Observable<any> {
    return this.http.delete(`${this.apiPath(repository.name)}`);
  }

  public update(repository: SoftwareRepositoryConfiguration): Observable<any> {
    return this.http.post(this.apiPath(repository.name), repository);
  }
}
