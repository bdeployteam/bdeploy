import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ProductDto } from '../../../../models/gen.dtos';
import { ProductService } from '../../services/product.service';

@Component({
  selector: 'app-product-card',
  templateUrl: './product-card.component.html',
  styleUrls: ['./product-card.component.css'],
})
export class ProductCardComponent implements OnInit {
  @Input() public instanceGroup: string;
  @Input() public product: string;
  @Input() public productVersions: ProductDto[];
  @Output() public select = new EventEmitter();

  public diskUsage = '(...)';

  public get latestProductVersion() {
    return this.productVersions && this.productVersions.length > 0 ? this.productVersions[0] : null;
  }

  constructor(private productService: ProductService) {}

  ngOnInit() {
    // TODO: what if there are products with the same name but different manifest key name?
    this.productService.getProductDiskUsage(this.instanceGroup, this.productVersions[0].key).subscribe(r => {
      this.diskUsage = r;
    });
  }

  clickIt(): void {
    this.select.emit();
  }
}
