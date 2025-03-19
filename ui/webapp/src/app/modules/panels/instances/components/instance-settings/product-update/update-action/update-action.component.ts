import { Component, Input, OnInit, inject } from '@angular/core';
import { ProductDto } from 'src/app/models/gen.dtos';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  CellComponent
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-update-action',
    templateUrl: './update-action.component.html',
    imports: [BdButtonComponent]
})
export class UpdateActionComponent implements OnInit, CellComponent<ProductDto, string> {
  private readonly products = inject(ProductsService);
  private readonly edit = inject(InstanceEditService);

  @Input() record: ProductDto;
  @Input() column: BdDataColumn<ProductDto, string>;

  private index: number;
  private curIndex: number;
  protected isUpgrade: boolean;
  protected isCurrent: boolean;

  ngOnInit(): void {
    const products = this.products.products$.value || [];
    this.index = products.indexOf(this.record);
    this.curIndex = products.findIndex(
      (r) =>
        this.edit.state$.value?.config.config.product.name === r.key.name &&
        this.edit.state$.value?.config.config.product.tag === r.key.tag,
    );
    this.isUpgrade = this.index < this.curIndex;
    this.isCurrent = this.index === this.curIndex;
  }

  protected doUpdate() {
    this.edit.updateProduct(this.record);
  }
}
