import { Component, Input, OnInit } from '@angular/core';
import { ProductDto } from 'src/app/models/gen.dtos';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Component({
  selector: 'app-update-action',
  templateUrl: './update-action.component.html',
  styleUrls: ['./update-action.component.css'],
})
export class UpdateActionComponent implements OnInit {
  @Input() record: ProductDto;

  private index: number;
  private curIndex: number;
  /* template */ isUpgrade: boolean;
  /* template */ isCurrent: boolean;

  constructor(private products: ProductsService, private edit: InstanceEditService) {}

  ngOnInit(): void {
    this.index = this.products.products$.value.indexOf(this.record);
    this.curIndex = this.products.products$.value.findIndex(
      (r) => this.edit.state$.value?.config.config.product.name === r.key.name && this.edit.state$.value?.config.config.product.tag === r.key.tag
    );
    this.isUpgrade = this.index < this.curIndex;
    this.isCurrent = this.index === this.curIndex;
  }

  /* template */ doUpdate() {
    this.edit.updateProduct(this.record);
  }
}
