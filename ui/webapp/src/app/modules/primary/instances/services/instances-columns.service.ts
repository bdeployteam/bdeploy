import { Injectable, inject } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { InstanceDto, InstancePurpose, ManagedMasterDto, ManifestKey, MinionMode } from 'src/app/models/gen.dtos';
import { BdIdentifierCellComponent } from 'src/app/modules/core/components/bd-identifier-cell/bd-identifier-cell.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { BdDataSyncCellComponent } from '../../../core/components/bd-data-sync-cell/bd-data-sync-cell.component';
import { ProductsService } from '../../products/services/products.service';
import { SystemsService } from '../../systems/services/systems.service';
import { InstanceBannerHintComponent } from '../components/browser/instance-banner-hint/instance-banner-hint.component';
import { InstanceManagedServerComponent } from '../components/browser/instance-managed-server/instance-managed-server.component';
import { InstanceProductVersionComponent } from '../components/browser/instance-product-version/instance-product-version.component';
import { InstancePurposeShortComponent } from '../components/browser/instance-purpose-short/instance-purpose-short.component';

@Injectable({
  providedIn: 'root',
})
export class InstancesColumnsService {
  private readonly cfg = inject(ConfigService);
  private readonly products = inject(ProductsService);
  private readonly systems = inject(SystemsService);

  public readonly instanceTypeColumn: BdDataColumn<InstanceDto, InstancePurpose> = {
    id: 'type',
    name: 'Purp.',
    description: 'Purpose',
    hint: BdDataColumnTypeHint.TYPE,
    data: (r) => r.instanceConfiguration.purpose,
    width: '20px',
    showWhen: '(min-width: 1000px)',
    component: InstancePurposeShortComponent,
  };

  public readonly instanceNameColumn: BdDataColumn<InstanceDto, string> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.instanceConfiguration.name,
    sortCard: true,
  };

  public readonly instanceIdColumn: BdDataColumn<InstanceDto, string> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.instanceConfiguration.id,
    width: '75px',
    showWhen: '(min-width: 1900px)',
    component: BdIdentifierCellComponent,
  };

  public readonly instanceDescriptionColumn: BdDataColumn<InstanceDto, string> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.instanceConfiguration.description,
    showWhen: '(min-width: 1400px)',
    sortCard: true,
  };

  public readonly instanceSystemColumn: BdDataColumn<InstanceDto, string> = {
    id: 'system',
    name: 'System',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => this.getSystemName(r.instanceConfiguration.system),
    showWhen: '(min-width: 1900px)',
  };

  public readonly instanceProductColumn: BdDataColumn<InstanceDto, string> = {
    id: 'product',
    name: 'Product',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) =>
      this.products.products$.value.find(
        (p) => p.key.name === r.instanceConfiguration.product.name && p.key.tag === r.instanceConfiguration.product.tag,
      )?.name || r.instanceConfiguration.product.name,
    icon: () => 'apps',
    showWhen: '(min-width: 600px)',
  };

  public readonly instanceProductVersionColumn: BdDataColumn<InstanceDto, string> = {
    id: 'productVersion',
    name: 'Product Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => r.instanceConfiguration.product.tag,
    component: InstanceProductVersionComponent,
    icon: () => 'smartphone',
  };

  public readonly instanceProductActiveColumn: BdDataColumn<InstanceDto, string> = {
    id: 'activeProductVersion',
    name: 'Active Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (r.activeProduct ? r.activeProduct.tag : null),
    icon: () => 'security_update_good',
    showWhen: '(min-width: 1000px)',
    sortCard: true,
  };

  public readonly instanceBannerColumn: BdDataColumn<InstanceDto, string> = {
    id: 'bannerHint',
    name: 'Ban.',
    description: 'Banner',
    data: (r) => r.banner?.text,
    component: InstanceBannerHintComponent,
    width: '24px',
  };

  public readonly instanceServerColumn: BdDataColumn<InstanceDto, string> = {
    id: 'managedServer',
    name: 'Serv.',
    description: 'Managed Server',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (r.managedServer ? `${r.managedServer.hostName} - ${r.managedServer.description}` : null),
    icon: () => 'dns',
    component: InstanceManagedServerComponent,
    width: '24px',
  };

  public readonly instanceSyncColumn: BdDataColumn<InstanceDto, ManagedMasterDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => r.managedServer,
    component: BdDataSyncCellComponent,
    width: '50px',
  };

  constructor() {
    if (this.cfg.config.mode !== MinionMode.CENTRAL) {
      this.instanceSyncColumn.display = BdDataColumnDisplay.NONE;
      this.instanceServerColumn.display = BdDataColumnDisplay.NONE;
    }
  }

  private getSystemName(system: ManifestKey): string {
    if (!system) {
      return 'None';
    }
    return (
      this.systems.systems$.value?.find((s) => s.key?.name === system?.name && s.key?.tag === system?.tag)?.config
        ?.name || system?.name?.substring('meta/system/'.length)
    );
  }
}
