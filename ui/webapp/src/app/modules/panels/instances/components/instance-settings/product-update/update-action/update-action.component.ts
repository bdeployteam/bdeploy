import { Component, Input, OnInit } from '@angular/core';
import { ProductDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Component({
  selector: 'app-update-action',
  templateUrl: './update-action.component.html',
})
export class UpdateActionComponent implements OnInit {
  @Input() record: ProductDto;

  private index: number;
  private curIndex: number;
  /* template */ isUpgrade: boolean;
  /* template */ isCurrent: boolean;

  constructor(
    private products: ProductsService,
    private edit: InstanceEditService,
    private areas: NavAreasService
  ) {}

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
  }

  /* template */ doUpdate() {
    this.edit.updateProduct(this.record);
  }
}
