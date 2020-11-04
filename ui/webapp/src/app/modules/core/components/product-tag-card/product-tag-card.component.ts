import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { isEqual } from 'lodash-es';
import { ManifestKey, ProductDto } from '../../../../models/gen.dtos';

@Component({
  selector: 'app-product-tag-card',
  templateUrl: './product-tag-card.component.html',
  styleUrls: ['./product-tag-card.component.css'],
})
export class ProductTagCardComponent implements OnInit {
  @Input() public instanceGroup: string;
  @Input() public product: ProductDto;
  @Input() public current: ManifestKey;
  @Input() public products: ProductDto[];
  @Output() public select = new EventEmitter<ProductDto>();

  public isCurrent = false;
  public isUpgrade = false;
  public isDowngrade = false;

  constructor() {}

  async ngOnInit() {
    const currentIndex = this.products.findIndex((p) => isEqual(this.current, p.key));
    const myIndex = this.products.indexOf(this.product);

    this.isCurrent = myIndex === currentIndex;
    this.isUpgrade = myIndex < currentIndex;
    this.isDowngrade = myIndex > currentIndex;
  }
}
