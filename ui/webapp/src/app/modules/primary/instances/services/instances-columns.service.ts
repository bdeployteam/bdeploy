import { Injectable } from '@angular/core';
import {
  BdDataColumn,
  BdDataColumnDisplay,
  BdDataColumnTypeHint,
} from 'src/app/models/data';
import { InstanceDto, ManifestKey, MinionMode } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { BdDataSyncCellComponent } from '../../../core/components/bd-data-sync-cell/bd-data-sync-cell.component';
import { ProductsService } from '../../products/services/products.service';
import { ServersService } from '../../servers/services/servers.service';
import { SystemsService } from '../../systems/services/systems.service';
import { InstanceBannerHintComponent } from '../components/browser/instance-banner-hint/instance-banner-hint.component';
import { InstanceProductVersionComponent } from '../components/browser/instance-product-version/instance-product-version.component';
import { InstancePurposeShortComponent } from '../components/browser/instance-purpose-short/instance-purpose-short.component';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class InstancesColumnsService {
  instanceTypeColumn: BdDataColumn<InstanceDto> = {
    id: 'type',
    name: 'Purpose',
    hint: BdDataColumnTypeHint.TYPE,
    data: (r) => r.instanceConfiguration.purpose,
    width: '20px',
    showWhen: '(min-width: 1000px)',
    component: InstancePurposeShortComponent,
  };

  instanceNameColumn: BdDataColumn<InstanceDto> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.instanceConfiguration.name,
    sortCard: true,
  };

  instanceIdColumn: BdDataColumn<InstanceDto> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.instanceConfiguration.id,
    width: '110px',
    showWhen: '(min-width: 2100px)',
  };

  instanceDescriptionColumn: BdDataColumn<InstanceDto> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.instanceConfiguration.description,
    showWhen: '(min-width: 1400px)',
    sortCard: true,
  };

  instanceSystemColumn: BdDataColumn<InstanceDto> = {
    id: 'system',
    name: 'System',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => this.getSystemName(r.instanceConfiguration.system),
    showWhen: '(min-width: 1900px)',
  };

  instanceProductColumn: BdDataColumn<InstanceDto> = {
    id: 'product',
    name: 'Product',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) =>
      this.products.products$.value.find(
        (p) =>
          p.key.name === r.instanceConfiguration.product.name &&
          p.key.tag === r.instanceConfiguration.product.tag
      )?.name || r.instanceConfiguration.product.name,
    icon: () => 'apps',
    showWhen: '(min-width: 600px)',
  };

  instanceProductVersionColumn: BdDataColumn<InstanceDto> = {
    id: 'productVersion',
    name: 'Product Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => r.instanceConfiguration.product.tag,
    component: InstanceProductVersionComponent,
    icon: () => 'smartphone',
  };

  instanceProductActiveColumn: BdDataColumn<InstanceDto> = {
    id: 'activeProductVersion',
    name: 'Active Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (r.activeProduct ? r.activeProduct.tag : null),
    icon: () => 'security_update_good',
    showWhen: '(min-width: 750px)',
    sortCard: true,
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
    data: (r) =>
      r.managedServer
        ? `${r.managedServer.hostName} - ${r.managedServer.description}`
        : null,
    icon: () => 'dns',
    showWhen: '(min-width: 2000px)',
  };

  instanceSyncColumn: BdDataColumn<InstanceDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => r.managedServer,
    component: BdDataSyncCellComponent,
    width: '50px',
  };

  constructor(
    private cfg: ConfigService,
    private instances: InstancesService,
    private servers: ServersService,
    private products: ProductsService,
    private systems: SystemsService
  ) {
    if (cfg.config.mode !== MinionMode.CENTRAL) {
      this.instanceSyncColumn.display = BdDataColumnDisplay.NONE;
      this.instanceServerColumn.display = BdDataColumnDisplay.NONE;
    }
  }

  private getSystemName(system: ManifestKey): string {
    if (!system) {
      return 'None';
    }
    return (
      this.systems.systems$.value?.find(
        (s) => s.key?.name === system?.name && s.key?.tag === system?.tag
      )?.config?.name || system?.name?.substring('meta/system/'.length)
    );
  }
}
