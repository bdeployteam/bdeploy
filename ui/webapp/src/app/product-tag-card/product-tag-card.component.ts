import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ManifestKey, ProductDto } from '../models/gen.dtos';
import { compareTags } from '../utils/manifest.utils';

@Component({
  selector: 'app-product-tag-card',
  templateUrl: './product-tag-card.component.html',
  styleUrls: ['./product-tag-card.component.css']
})
export class ProductTagCardComponent implements OnInit {

  @Input() public product: ProductDto;
  @Input() public current: ManifestKey;
  @Output() public select = new EventEmitter<ProductDto>();

  public isCurrent = false;
  public isUpgrade = false;
  public isDowngrade = false;

  constructor() { }

  ngOnInit() {
    const c = compareTags(this.current.tag, this.product.key.tag);
    this.isCurrent = c === 0;
    this.isUpgrade = c < 0;
    this.isDowngrade = c > 0;
  }

}
