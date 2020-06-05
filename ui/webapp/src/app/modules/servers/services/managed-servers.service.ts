import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { InstanceConfiguration, ManagedMasterDto, MinionDto, MinionStatusDto, MinionUpdateDto, ProductDto, ProductTransferDto, Version } from '../../../models/gen.dtos';
import { ConfigService } from '../../core/services/config.service';
import { suppressGlobalErrorHandling } from '../../shared/utils/server.utils';

@Injectable({
  providedIn: 'root',
})
export class ManagedServersService {
  constructor(private config: ConfigService, private http: HttpClient) {}

  public getManagedMasterInfo(): Observable<ManagedMasterDto> {
    return this.http.get<ManagedMasterDto>(this.config.config.api + '/backend-info/managed-master');
  }

  public tryAutoAttach(group: string, ident: ManagedMasterDto): Observable<any> {
    return this.http.put(this.config.config.api + '/managed-servers/auto-attach/' + group, ident, {
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    });
  }

  public manualAttach(group: string, ident: ManagedMasterDto): Observable<any> {
    return this.http.put(this.config.config.api + '/managed-servers/manual-attach/' + group, ident);
  }

  public manualAttachCentral(ident: string): Observable<string> {
    return this.http.put(this.config.config.api + '/managed-servers/manual-attach-central', ident, {
      responseType: 'text',
    });
  }

  public getCentralIdent(group: string, ident: ManagedMasterDto): Observable<string> {
    return this.http.post(this.config.config.api + '/managed-servers/central-ident/' + group, ident, {
      responseType: 'text',
    });
  }

  public getManagedServers(group: string): Observable<ManagedMasterDto[]> {
    return this.http.get<ManagedMasterDto[]>(this.config.config.api + '/managed-servers/list/' + group);
  }

  public getServerForInstance(group: string, instance: string, tag: string): Observable<ManagedMasterDto> {
    const p = new HttpParams();
    if (tag) {
      p.set('instanceTag', tag);
    }
    return this.http.get<ManagedMasterDto>(
      this.config.config.api + '/managed-servers/controlling-server/' + group + '/' + instance,
      { params: p, headers: new HttpHeaders({'ignoreLoadingBar': ''}) },
    );
  }

  public getInstancesForManagedServer(group: string, server: string): Observable<InstanceConfiguration[]> {
    return this.http.get<InstanceConfiguration[]>(
      this.config.config.api + '/managed-servers/controlled-instances/' + group + '/' + server,
    );
  }

  public deleteManagedServer(group: string, server: string): Observable<any> {
    return this.http.post(this.config.config.api + '/managed-servers/delete-server/' + group + '/' + server, server);
  }

  public updateManagedServer(group: string, server: string, dto: ManagedMasterDto): Observable<any> {
    return this.http.post(this.config.config.api + '/managed-servers/update-server/' + group + '/' + server, dto);
  }

  public minionsConfigOfManagedServer(group: string, server: string): Observable<{ [minionName: string]: MinionDto }> {
    return this.http.get<{ [minionName: string]: MinionDto }>(
      this.config.config.api + '/managed-servers/minion-config/' + group + '/' + server,
    );
  }

  public minionsStateOfManagedServer(
    group: string,
    server: string,
  ): Observable<{ [minionName: string]: MinionStatusDto }> {
    return this.http.get<{ [minionName: string]: MinionStatusDto }>(
      this.config.config.api + '/managed-servers/minion-state/' + group + '/' + server,
    );
  }

  public synchronize(group: string, server: string): Observable<ManagedMasterDto> {
    return this.http.post<ManagedMasterDto>(
      this.config.config.api + '/managed-servers/synchronize/' + group + '/' + server,
      server,
      {
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      },
    );
  }

  public productsOfManagedServer(group: string, server: string): Observable<ProductDto[]> {
    return this.http.get<ProductDto[]>(
      this.config.config.api + '/managed-servers/list-products/' + group + '/' + server,
    );
  }

  public productsInTransfer(group: string): Observable<ProductDto[]> {
    return this.http.get<ProductDto[]>(this.config.config.api + '/managed-servers/active-transfers/' + group);
  }

  public startTransfer(group: string, data: ProductTransferDto): Observable<any> {
    return this.http.post(this.config.config.api + '/managed-servers/transfer-products/' + group, data, {
      headers: new HttpHeaders({ 'X-Proxy-Activity-Scope': group + ',' + 'transfer' }),
    });
  }

  public transferUpdate(group: string, server: string, update: MinionUpdateDto): Observable<any> {
    return this.http.post(this.config.config.api + '/managed-servers/minion-transfer-updates/' + group + '/' + server, update);
  }

  public installUpdate(group: string, server: string, update: MinionUpdateDto): Observable<any> {
    return this.http.post(this.config.config.api + '/managed-servers/minion-install-updates/' + group + '/' + server, update);
  }

  public ping(group: string, server: string): Observable<Version> {
    return this.http.get<Version>(
      this.config.config.api + '/managed-servers/minion-ping/' + group + '/' + server, {
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      },
    );
  }

  public isDataMigrationRequired(group: string): Observable<boolean> {
    return this.http.get<boolean>(this.config.config.api + '/managed-servers/requires-data-migration/' + group);
  }

  public performDataMigration(group: string): Observable<any> {
    return this.http.post(this.config.config.api + '/managed-servers/perform-data-migration/' + group, {});
  }
}
