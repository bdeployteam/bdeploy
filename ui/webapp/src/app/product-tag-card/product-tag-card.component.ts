import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ManifestKey, ProductDto } from '../models/gen.dtos';

@Component({
  selector: 'app-product-tag-card',
  templateUrl: './product-tag-card.component.html',
  styleUrls: ['./product-tag-card.component.css']
})
export class ProductTagCardComponent implements OnInit {

  @Input() public product: ProductDto;
  @Input() public current: ManifestKey;
  @Output() public select = new EventEmitter<ProductDto>();

  constructor() { }

  ngOnInit() {
  }

}
