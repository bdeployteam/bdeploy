import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ClientDescriptor, DeploymentStateDto, InstanceConfiguration, InstanceConfigurationDto, InstanceNodeConfigurationListDto, InstancePurpose, InstanceVersionDto, ManifestKey } from '../models/gen.dtos';
import { ConfigService } from './config.service';
import { InstanceGroupService } from './instance-group.service';
import { Logger, LoggingService } from './logging.service';

@Injectable({
  providedIn: 'root',
})
export class InstanceService {
  private readonly log: Logger = this.loggingService.getLogger('InstanceService');

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) {}

  public listInstances(instanceGroupName: string): Observable<InstanceConfiguration[]> {
    const url: string = this.buildGroupUrl(instanceGroupName);
    this.log.debug('listInstances: ' + url);
    return this.http.get<InstanceConfiguration[]>(url);
  }

  public createInstance(instanceGroupName: string, instance: InstanceConfiguration) {
    const url: string = this.buildGroupUrl(instanceGroupName);
    this.log.debug('createInstance: ' + url);
    return this.http.put(url, instance);
  }

  public getInstance(instanceGroupName: string, instanceName: string): Observable<InstanceConfiguration> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName);
    this.log.debug('getInstance: ' + url);
    return this.http.get<InstanceConfiguration>(url);
  }

  public updateInstance(instanceGroupName: string, instanceName: string, instance: InstanceConfiguration, nodeList: InstanceNodeConfigurationListDto) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName);
    this.log.debug('updateInstance: ' + url);
    const dto: InstanceConfigurationDto = {
      config: instance,
      nodeDtos: nodeList ? nodeList.nodeConfigDtos : null
    };
    return this.http.post(url, dto);
  }

  public deleteInstance(instanceGroupName: string, instanceName: string) {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName);
    this.log.debug('deleteInstance: ' + url);
    return this.http.delete(url);
  }

  public listInstanceVersions(instanceGroupName: string, instanceName: string): Observable<InstanceVersionDto[]> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/versions';
    this.log.debug('listInstanceVersions: ' + url);
    return this.http.get<InstanceVersionDto[]>(url);
  }

  public getInstanceVersion(instanceGroupName: string,
    instanceName: string,
    tag: string,
  ): Observable<InstanceConfiguration> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/' + tag;
    this.log.debug('getInstanceVersion: ' + url);
    return this.http.get<InstanceConfiguration>(url);
  }

  public listPurpose(instanceGroupName: string): Observable<InstancePurpose[]> {
    const url: string = this.buildGroupUrl(instanceGroupName) + '/purposes';
    return this.http.get<InstancePurpose[]>(url);
  }

  public getNodeConfiguration(
    instanceGroupName: string,
    instanceId: string,
    tag: string,
  ): Observable<InstanceNodeConfigurationListDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceId) + '/' + tag + '/nodeConfiguration';
    this.log.debug('getNodeConfigurationVersion: ' + url);
    return this.http.get<InstanceNodeConfigurationListDto>(url);
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

  public getDeploymentStates(instanceGroupName: string, instanceName: string): Observable<DeploymentStateDto> {
    const url: string = this.buildInstanceUrl(instanceGroupName, instanceName) + '/state';
    this.log.debug('getDeploymentStates: ' + url);
    return this.http.get<DeploymentStateDto>(url);
  }

  public getNewClientDescriptor(instanceGroupName: string, instanceName: string, processId: string): Observable<ClientDescriptor> {
    const url = this.buildInstanceUrl(instanceGroupName, instanceName) + '/launcher/' + processId;
    this.log.debug('getNewClientDescriptor: ' + url);
    return this.http.get<ClientDescriptor>(url);
  }

  public buildGroupUrl(instanceGroupName: string): string {
    return this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + instanceGroupName + '/instance';
  }

  public buildInstanceUrl(instanceGroupName: string, instanceName: string): string {
    return this.buildGroupUrl(instanceGroupName) + '/' + instanceName;
  }
}
