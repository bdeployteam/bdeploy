import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CustomAttributesRecord, InstanceGroupConfiguration, RepairAndPruneResultDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';

@Injectable({
  providedIn: 'root',
})
export class GroupDetailsService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);

  private hiveApiPath = `${this.cfg.config.api}/hive`;
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}`;

  public delete(group: InstanceGroupConfiguration): Observable<any> {
    return this.http.delete(`${this.apiPath(group.name)}`);
  }

  public update(group: InstanceGroupConfiguration): Observable<any> {
    return this.http.post(this.apiPath(group.name), group);
  }

  public updateAttributes(group: string, attributes: CustomAttributesRecord) {
    return this.http.post(`${this.apiPath(group)}/attributes`, attributes);
  }

  public repairAndPrune(hive: string): Observable<RepairAndPruneResultDto> {
    return this.http.get<RepairAndPruneResultDto>(`${this.hiveApiPath}/repair-and-prune`, {
      params: { hive, fix: 'true' },
    });
  }
}
