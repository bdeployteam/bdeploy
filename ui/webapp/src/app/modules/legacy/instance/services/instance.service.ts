import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { Observable, of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { EMPTY_HISTORY_COMPARE, EMPTY_HISTORY_FILTER } from 'src/app/models/consts';
import {
  ClickAndStartDescriptor,
  ConfigFileDto,
  CustomAttributesRecord,
  FileStatusDto,
  HistoryCompareDto,
  HistoryEntryVersionDto,
  HistoryFilterDto,
  HistoryResultDto,
  InstanceBannerRecord,
  InstanceConfiguration,
  InstanceConfigurationDto,
  InstanceDto,
  InstanceManifestHistoryDto,
  InstanceNodeConfigurationListDto,
  InstancePurpose,
  InstanceStateRecord,
  InstanceVersionDto,
  ManifestKey,
  MinionDto,
  MinionStatusDto,
  RemoteDirectory,
  RemoteDirectoryEntry,
  StringEntryChunkDto,
} from '../../../../models/gen.dtos';
import { MessageBoxMode } from '../../../core/components/messagebox/messagebox.component';
import { ConfigService } from '../../../core/services/config.service';
import { DownloadService } from '../../../core/services/download.service';
import { ErrorMessage, Logger, LoggingService } from '../../../core/services/logging.service';
import { MessageboxService } from '../../../core/services/messagebox.service';
import { SystemService } from '../../../core/services/system.service';
import { suppressGlobalErrorHandling } from '../../../legacy/shared/utils/server.utils';
import { InstanceGroupService } from '../../instance-group/services/instance-group.service';

@Injectable({
  providedIn: 'root',
})
export class InstanceService {
  private readonly log: Logger = this.loggingService.getLogger('InstanceService');

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
    private downloadService: DownloadService,
    private systemService: SystemService,
    private messageBoxService: MessageboxService
  ) {}

  public listInstances(instanceGroupName: string): Observable<InstanceDto[]> {
    const url: string = this.buildGroupUrl(instanceGroupName);
    this.log.debug('listInstances: ' + url);
    return this.http.get<InstanceDto[]>(url);
  }

  public createInstance(instanceGroupName: string, instance: InstanceConfiguration, managedServer: string) {
    const url: string = this.buildGroupUrl(instanceGroupName);
    this.log.debug('createInstance: ' + url);
    const options = {
      params: new HttpParams().set('managedServer', managedServer),
    };
    return this.http.put(url, instance, options);
  }

  public getInstance(instanceGroupName: string, instanceName: string): Observable<InstanceConfiguration> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName);
    this.log.debug('getInstance: ' + url);
    return this.http.get<InstanceConfiguration>(url);
  }

  public updateInstance(
    instanceGroupName: string,
    instanceName: string,
    instance: InstanceConfiguration,
    nodeList: InstanceNodeConfigurationListDto,
    managedServer: string,
    expectedTag: string
  ) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName);
    this.log.debug('updateInstance: ' + url);
    const dto: InstanceConfigurationDto = {
      config: instance,
      nodeDtos: nodeList ? nodeList.nodeConfigDtos : null,
    };
    const options = {
      params: new HttpParams().set('expect', expectedTag).set('managedServer', managedServer),
      headers: suppressGlobalErrorHandling(new HttpHeaders()),
    };
    return this.http.post(url, dto, options).pipe(
      catchError((e) => {
        if (e instanceof HttpErrorResponse && e.status !== 401) {
          if (e.status === 409) {
            this.messageBoxService
              .open({
                title: 'Conflict',
                mode: MessageBoxMode.ERROR,
                message:
                  'There has been a conflict while saving. Another session has modified the instance, and your changes cannot be saved.',
              })
              .subscribe((_) => {});
            return throwError(e);
          } else if (e.status === 0) {
            this.systemService.backendUnreachable();
          } else {
            const displayPath = new URL(url).pathname;
            this.log.errorWithGuiMessage(new ErrorMessage(e.status + ': ' + e.statusText + ': ' + displayPath, e));
          }
          return of(null);
        }
        return throwError(e);
      })
    );
  }

  public deleteInstance(instanceGroupName: string, instanceName: string) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/delete';
    this.log.debug('deleteInstance: ' + url);
    return this.http.delete(url);
  }

  public deleteInstanceVersion(instanceGroupName: string, instanceName: string, instanceTag: string) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/deleteVersion/' + instanceTag;
    this.log.debug('deleteInstanceVersion: ' + url);
    return this.http.delete(url);
  }

  public listInstanceVersions(instanceGroupName: string, instanceName: string): Observable<InstanceVersionDto[]> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/versions';
    this.log.debug('listInstanceVersions: ' + url);
    return this.http.get<InstanceVersionDto[]>(url);
  }

  public getInstanceVersion(
    instanceGroupName: string,
    instanceName: string,
    tag: string
  ): Observable<InstanceConfiguration> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + tag;
    this.log.debug('getInstanceVersion: ' + url);
    return this.http.get<InstanceConfiguration>(url);
  }

  public listConfigurationFiles(
    instanceGroupName: string,
    instanceName: string,
    tag: string
  ): Observable<ConfigFileDto[]> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/cfgFiles/' + tag;
    this.log.debug('listConfigurationFiles: ' + url);
    return this.http.get<ConfigFileDto[]>(url);
  }

  public syncConfigurationFiles(
    instanceGroupName: string,
    instanceName: string,
    tag: string,
    pKey: ManifestKey
  ): Observable<ConfigFileDto[]> {
    const url: string =
      this.buildInstanceUrl(instanceGroupName, instanceName) +
      '/cfgFiles/' +
      tag +
      '/' +
      pKey.name +
      '/' +
      pKey.tag +
      '/syncConfig';
    this.log.debug('syncConfigurationFiles: ' + url);
    return this.http.get<ConfigFileDto[]>(url);
  }

  public getConfigurationFile(
    instanceGroupName: string,
    instanceName: string,
    tag: string,
    filename: string
  ): Observable<string> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/cfgFiles/' + tag + '/' + filename;
    this.log.debug('getConfigurationFile: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public updateConfigurationFiles(
    instanceGroupName: string,
    instanceName: string,
    tag: string,
    configFiles: FileStatusDto[]
  ) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/cfgFiles';
    this.log.debug('updateConfigurationFiles: ' + url);
    const options = {
      params: new HttpParams().set('expect', tag),
    };
    return this.http.post(url, configFiles, options);
  }

  public listDataDirSnapshot(instanceGroupName: string, instanceName: string): Observable<RemoteDirectory[]> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/processes/dataDirSnapshot';
    this.log.debug('getDataDirSnapshot: ' + url);
    return this.http.get<RemoteDirectory[]>(url);
  }

  public listPurpose(instanceGroupName: string): Observable<InstancePurpose[]> {
    const url: string = this.buildGroupUrl(instanceGroupName) + '/purposes';
    return this.http.get<InstancePurpose[]>(url);
  }

  public getNodeConfiguration(
    instanceGroupName: string,
    instanceId: string,
    tag: string
  ): Observable<InstanceNodeConfigurationListDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/' + tag + '/nodeConfiguration';
    this.log.debug('getNodeConfigurationVersion: ' + url);
    return this.http.get<InstanceNodeConfigurationListDto>(url);
  }

  public getMinionConfiguration(
    instanceGroupName: string,
    instanceId: string,
    tag: string
  ): Observable<{ [key: string]: MinionDto }> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/' + tag + '/minionConfiguration';
    return this.http.get<{ [minionName: string]: MinionDto }>(url);
  }

  public getMinionState(
    instanceGroupName: string,
    instanceId: string,
    tag: string
  ): Observable<{ [key: string]: MinionStatusDto }> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/' + tag + '/minionState';
    return this.http.get<{ [minionName: string]: MinionStatusDto }>(url);
  }

  public install(instanceGroupName: string, instanceName: string, instance: ManifestKey) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + instance.tag + '/install';
    this.log.debug('install: ' + url);
    return this.http.get(url);
  }

  public uninstall(instanceGroupName: string, instanceName: string, instance: ManifestKey) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + instance.tag + '/uninstall';
    this.log.debug('uninstall: ' + url);
    return this.http.get(url);
  }

  public activate(instanceGroupName: string, instanceName: string, instance: ManifestKey) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + instance.tag + '/activate';
    this.log.debug('activate: ' + url);
    return this.http.get(url);
  }

  public getHistory(
    instanceGroupName: string,
    instanceName: string,
    instance: ManifestKey
  ): Observable<InstanceManifestHistoryDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + instance.tag + '/history';
    this.log.debug('history: ' + url);
    return this.http.get<InstanceManifestHistoryDto>(url);
  }

  public getDeploymentStates(instanceGroupName: string, instanceName: string): Observable<InstanceStateRecord> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/state';
    this.log.debug('getDeploymentStates: ' + url);
    return this.http.get<InstanceStateRecord>(url);
  }

  public getApplicationOutputEntry(
    instanceGroupName: string,
    instanceName: string,
    instanceTag: string,
    appUid: string,
    silent: boolean
  ): Observable<RemoteDirectory> {
    const url: string =
      this.buildInstanceUrl(instanceGroupName, instanceName) + '/output/' + instanceTag + '/' + appUid;
    this.log.debug('getApplicationOutputEntry: ' + url);
    const options = {
      headers: { ignoreLoadingBar: '' },
    };
    return this.http.get<RemoteDirectory>(url, silent ? options : {});
  }

  public getContentChunk(
    instanceGroupName: string,
    instanceName: string,
    rd: RemoteDirectory,
    rde: RemoteDirectoryEntry,
    offset: number,
    limit: number,
    silent: boolean
  ): Observable<StringEntryChunkDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/content/' + rd.minion;
    this.log.debug('getContentChunk: ' + url);
    const options = {
      headers: null,
      params: new HttpParams().set('offset', offset.toString()).set('limit', limit.toString()),
    };
    if (silent) {
      options.headers = { ignoreLoadingBar: '' };
    }
    return this.http.post<StringEntryChunkDto>(url, rde, options);
  }

  public downloadDataFileContent(
    instanceGroupName: string,
    instanceName: string,
    rd: RemoteDirectory,
    rde: RemoteDirectoryEntry
  ) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/request/' + rd.minion;
    this.log.debug('downloadDataFileContent');
    this.http.post(url, rde, { responseType: 'text' }).subscribe((token) => {
      this.downloadService.download(this.buildInstanceUrl(instanceGroupName, instanceName) + '/stream/' + token);
    });
  }

  public deleteDataFile(
    instanceGroupName: string,
    instanceName: string,
    rd: RemoteDirectory,
    rde: RemoteDirectoryEntry
  ) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/delete/' + rd.minion;
    this.log.debug('deleteDataFile');
    return this.http.post(url, rde);
  }

  public createClickAndStartDescriptor(
    instanceGroupName: string,
    instanceName: string,
    appId: string
  ): Observable<ClickAndStartDescriptor> {
    const url = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + appId + '/clickAndStart';
    this.log.debug('createClickAndStartDescriptor: ' + url);
    return this.http.get<ClickAndStartDescriptor>(url);
  }

  public createClientInstaller(instanceGroupName: string, instanceName: string, appId: string): Observable<string> {
    const url = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + appId + '/installer/zip';
    this.log.debug('createClientInstaller: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public downloadClientInstaller(token: string) {
    const url = this.downloadService.createDownloadUrl(token);
    this.downloadService.download(url);
  }

  public getExportUrl(instanceGroupName: string, instanceName: string, tag: string) {
    return this.buildInstanceUrl(instanceGroupName, instanceName) + '/export/' + tag;
  }

  public getImportUrl(instanceGroupName: string, instanceName: string) {
    return this.buildInstanceUrl(instanceGroupName, instanceName) + '/import';
  }

  /**
   * Fetches the states of each given application's server ports on the target minion.
   * <p>
   * The result is grouped by application (UUID) and contains a state per port (true/false).
   */
  public getOpenPorts(
    instanceGroup: string,
    instance: string,
    minion: string,
    ports: string[]
  ): Observable<{ [key: number]: boolean }> {
    const url = this.buildInstanceUrl(instanceGroup, instance) + '/check-ports/' + minion;
    return this.http.post<{ [key: number]: boolean }>(url, ports);
  }

  public getInstanceBanner(instanceGroupName: string, instanceName: string): Observable<InstanceBannerRecord> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/banner';
    this.log.debug('getInstanceBanner: ' + url);
    return this.http.get<InstanceBannerRecord>(url);
  }

  public updateInstanceBanner(instanceGroupName: string, instanceName: string, instanceBanner: InstanceBannerRecord) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/banner';
    this.log.debug('updateInstanceBanner: ' + url);
    return this.http.post(url, instanceBanner);
  }

  public buildGroupUrl(instanceGroupName: string): string {
    return this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + instanceGroupName + '/instance';
  }

  public buildInstanceUrl(instanceGroupName: string, instanceName: string): string {
    return this.buildGroupUrl(instanceGroupName) + '/' + instanceName;
  }

  public getInstanceHistory(
    instanceGroupName: string,
    instanceId: string,
    maxResults: number,
    startTag: string,
    filter: string,
    showCreate: boolean,
    showDeployment: boolean,
    showRuntime: boolean
  ): Observable<HistoryResultDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/history';

    const filterDto: HistoryFilterDto = cloneDeep(EMPTY_HISTORY_FILTER);
    filterDto.showCreateEvents = showCreate;
    filterDto.showDeploymentEvents = showDeployment;
    filterDto.showRuntimeEvents = showRuntime;
    filterDto.maxResults = maxResults;
    filterDto.startTag = startTag;
    filterDto.filterText = filter;
    return this.http.post<HistoryResultDto>(url, filterDto);
  }

  public compareVersions(
    instanceGroupName: string,
    instanceId: string,
    versionA: string,
    versionB: string
  ): Observable<HistoryEntryVersionDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/history-compare-versions';
    const params = new HttpParams().set('a', versionA.toString()).set('b', versionB.toString());
    return this.http.get<HistoryEntryVersionDto>(url, { params: params });
  }

  public compareConfigs(
    instanceGroupName: string,
    instanceId: string,
    configA: InstanceConfigurationDto,
    configB: InstanceConfigurationDto
  ): Observable<HistoryEntryVersionDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/history-compare-config';
    const dto: HistoryCompareDto = cloneDeep(EMPTY_HISTORY_COMPARE);
    dto.configA = configA;
    dto.configB = configB;
    return this.http.post<HistoryEntryVersionDto>(url, dto);
  }

  public listInstancesAttributes(instanceGroupName: string): Observable<{ [index: string]: CustomAttributesRecord }> {
    const url: string = this.buildGroupUrl(instanceGroupName) + '/list-attributes';
    this.log.debug('listInstancesAttributes: ' + url);
    return this.http.get<{ [index: string]: CustomAttributesRecord }>(url);
  }

  public getInstanceAttributes(instanceGroupName: string, instanceId: string): Observable<CustomAttributesRecord> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/attributes';
    this.log.debug('getInstanceAttributes: ' + url);
    return this.http.get<CustomAttributesRecord>(url);
  }

  public updateInstanceAttributes(instanceGroupName: string, instanceId: string, attributes: CustomAttributesRecord) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/attributes';
    this.log.debug('updateInstanceAttributes: ' + url);
    return this.http.post(url, attributes);
  }
}
