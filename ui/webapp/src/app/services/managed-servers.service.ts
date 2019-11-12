import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { InstanceConfiguration, ManagedMasterDto, NodeStatus } from '../models/gen.dtos';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class ManagedServersService {

  constructor(private config: ConfigService, private http: HttpClient) { }

  public getManagedMasterInfo(): Observable<ManagedMasterDto> {
    return this.http.get<ManagedMasterDto>(this.config.config.api + '/backend-info/managed-master');
  }

  public tryAutoAttach(group: string, ident: ManagedMasterDto): Observable<any> {
    return this.http.put(this.config.config.api + '/managed-servers/auto-attach/' + group, ident, { headers: suppressGlobalErrorHandling(new HttpHeaders)});
  }

  public manualAttach(group: string, ident: ManagedMasterDto): Observable<any> {
    return this.http.put(this.config.config.api + '/managed-servers/manual-attach/' + group, ident);
  }

  public manualAttachCentral(ident: string): Observable<string> {
    return this.http.put(this.config.config.api + '/managed-servers/manual-attach-central', ident, {responseType: 'text'});
  }

  public getCentralIdent(group: string, ident: ManagedMasterDto): Observable<string> {
    return this.http.post(this.config.config.api + '/managed-servers/central-ident/' + group, ident, { responseType: 'text' });
  }

  public getManagedServers(group: string): Observable<ManagedMasterDto[]> {
    return this.http.get<ManagedMasterDto[]>(this.config.config.api + '/managed-servers/list/' + group);
  }

  public getServerForInstance(group: string, instance: string, tag: string): Observable<ManagedMasterDto> {
    const p = new HttpParams();
    if (tag) {
      p.set('instanceTag', tag);
    }
    return this.http.get<ManagedMasterDto>(this.config.config.api + '/managed-servers/controlling-server/' + group + '/' + instance, {params: p});
  }

  public getInstancesForManagedServer(group: string, server: string): Observable<InstanceConfiguration[]> {
    return this.http.get<InstanceConfiguration[]>(this.config.config.api + '/managed-servers/controlled-instances/' + group + '/' + server);
  }

  public deleteManagedServer(group: string, server: string): Observable<any> {
    return this.http.post(this.config.config.api + '/managed-servers/delete-server/' + group + '/' + server, server);
  }

  public minionsOfManagedServer(group: string, server: string): Observable<{[key: string]: NodeStatus}> {
    return this.http.get<{[key: string]: NodeStatus}>(this.config.config.api + '/managed-servers/minions/' + group + '/' + server);
  }

  public synchronize(group: string, server: string) {
    return this.http.post(this.config.config.api + '/managed-servers/synchronize/' + group + '/' + server, server);
  }
}
