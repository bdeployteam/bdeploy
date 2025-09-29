import { Component, Input, OnInit, inject } from '@angular/core';
import { ProductDto } from 'src/app/models/gen.dtos';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { BdDataColumn } from '../../../../../../../models/data';
import { CellComponent } from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';
import { compareVersions, convert2String } from 'src/app/modules/core/utils/version.utils';
import { ConfigService } from 'src/app/modules/core/services/config.service';

@Component({
  selector: 'app-update-action',
  templateUrl: './update-action.component.html',
  imports: [BdButtonComponent],
})
export class UpdateActionComponent implements OnInit, CellComponent<ProductDto, string> {
  private readonly cfg = inject(ConfigService);
  private readonly products = inject(ProductsService);
  private readonly edit = inject(InstanceEditService);

  @Input() record: ProductDto;
  @Input() column: BdDataColumn<ProductDto, string>;

  private index: number;
  private curIndex: number;
  protected isUpgrade: boolean;
  protected isCurrent: boolean;
  protected hasMinMinionVersion: boolean;
  protected installButtonTooltip: string;

  ngOnInit(): void {
    const products = this.products.products$.value || [];
    this.index = products.indexOf(this.record);
    this.curIndex = products.findIndex(
      (r) =>
        this.edit.state$.value?.config.config.product.name === r.key.name &&
        this.edit.state$.value?.config.config.product.tag === r.key.tag
    );
    this.isUpgrade = this.index < this.curIndex;
    this.isCurrent = this.index === this.curIndex;

    this.installButtonTooltip = this.isCurrent
      ? 'This version is currently selected'
      : this.isUpgrade
      ? 'Upgrade to this product version'
      : 'Downgrade to this product version';

    const minimumVersion = this.record.minMinionVersion;
    if (minimumVersion) {
      const currentVersion = this.cfg?.config?.version;
      if (currentVersion) {
        this.hasMinMinionVersion = compareVersions(currentVersion, minimumVersion) >= 0;
        if (!this.hasMinMinionVersion) {
          this.installButtonTooltip =
            'This product version cannot be applied because it requires a BDeploy version of ' +
            convert2String(minimumVersion) +
            ' or above, but the current minion only has version ' +
            convert2String(currentVersion);
        }
      } else {
        this.hasMinMinionVersion = false;
        this.installButtonTooltip =
          'This product version cannot be applied because it requires a BDeploy version of ' +
          convert2String(minimumVersion) +
          ' or above, but the version of the current minion could not be determined';
      }
    } else {
      this.hasMinMinionVersion = true;
    }
  }

  protected doUpdate() {
    this.edit.updateProduct(this.record);
  }
}
