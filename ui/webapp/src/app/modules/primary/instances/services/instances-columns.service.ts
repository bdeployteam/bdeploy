import { Injectable } from '@angular/core';
import { format } from 'date-fns';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { InstanceDto, MinionMode } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ServersService } from '../../servers/services/servers.service';
import { InstanceBannerHintComponent } from '../components/browser/instance-banner-hint/instance-banner-hint.component';
import { InstanceProductVersionComponent } from '../components/browser/instance-product-version/instance-product-version.component';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class InstancesColumnsService {
  instanceTypeColumn: BdDataColumn<InstanceDto> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: (r) => r.instanceConfiguration.purpose,
    showWhen: '(min-width: 1000px)',
  };

  instanceNameColumn: BdDataColumn<InstanceDto> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.instanceConfiguration.name,
  };

  instanceIdColumn: BdDataColumn<InstanceDto> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.instanceConfiguration.uuid,
    width: '110px',
    showWhen: '(min-width: 1900px)',
  };

  instanceDescriptionColumn: BdDataColumn<InstanceDto> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.instanceConfiguration.description,
    showWhen: '(min-width: 1400px)',
  };

  instanceProductColumn: BdDataColumn<InstanceDto> = {
    id: 'product',
    name: 'Product',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (!!r.productDto?.name ? r.productDto.name : r.instanceConfiguration.product.name),
    icon: (r) => 'apps',
    showWhen: '(min-width: 600px)',
  };

  instanceProductVersionColumn: BdDataColumn<InstanceDto> = {
    id: 'productVersion',
    name: 'Product Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => r.instanceConfiguration.product.tag,
    component: InstanceProductVersionComponent,
    icon: (r) => 'smartphone',
  };

  instanceProductActiveColumn: BdDataColumn<InstanceDto> = {
    id: 'activeProductVersion',
    name: 'Active Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (!!r.activeProductDto ? r.activeProductDto.key.tag : null),
    icon: (r) => 'security_update_good',
    showWhen: '(min-width: 750px)',
  };

  instanceBannerColumn: BdDataColumn<InstanceDto> = {
    id: 'bannerHint',
    name: 'Banner',
    data: (r) => r.banner?.text,
    component: InstanceBannerHintComponent,
    width: '24px',
  };

  instanceServerColumn: BdDataColumn<InstanceDto> = {
    id: 'managedServer',
    name: 'Managed Server',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (!!r.managedServer ? `${r.managedServer.hostName} - ${r.managedServer.description}` : null),
    icon: (r) => 'dns',
    showWhen: '(min-width: 650px)',
  };

  instanceSyncColumn: BdDataColumn<InstanceDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => `Synchronize ${r.instanceConfiguration.name} - last synchronization ${format(new Date(r.managedServer.lastSync), 'dd.MM.yyyy HH:mm')}`,
    action: (r) => this.servers.synchronize(r.managedServer).subscribe(),
    classes: (r) => (this.servers.isSynchronized(r.managedServer) ? [] : ['bd-text-warn']),
    icon: (r) => 'history',
    width: '50px',
  };

  defaultInstancesColumns: BdDataColumn<InstanceDto>[] = [
    this.instanceNameColumn,
    this.instanceTypeColumn,
    this.instanceIdColumn,
    this.instanceDescriptionColumn,
    this.instanceProductColumn,
    this.instanceProductVersionColumn,
    this.instanceProductActiveColumn,
    this.instanceBannerColumn,
    this.instanceServerColumn,
    this.instanceSyncColumn,
  ];

  constructor(private cfg: ConfigService, private instances: InstancesService, private servers: ServersService) {
    if (cfg.config.mode !== MinionMode.CENTRAL) {
      this.instanceSyncColumn.display = BdDataColumnDisplay.NONE;
      this.instanceServerColumn.display = BdDataColumnDisplay.NONE;
    }
  }
}
